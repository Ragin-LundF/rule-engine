package ruleengine.compiler

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `every(collection[predicate])` and `any(collection[predicate])` — REQ-06. */
class CollectionPredicateTest {

    private val schema = FieldSchema(
        name = "predicate-schema",
        fields = mapOf(
            FieldId(value = "lineItems") to FieldDefinition(
                id = FieldId(value = "lineItems"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "quantity") to FieldDefinition(
                        id = FieldId(value = "quantity"),
                        type = FieldType.INTEGER
                    )
                )
            ),
            FieldId(value = "alerts") to FieldDefinition(
                id = FieldId(value = "alerts"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "severity") to FieldDefinition(
                        id = FieldId(value = "severity"),
                        type = FieldType.TEXT
                    )
                )
            ),
            FieldId(value = "threshold") to FieldDefinition(
                id = FieldId(value = "threshold"),
                type = FieldType.INTEGER
            )
        )
    )

    private fun items(vararg quantities: Int): List<Map<String, Any?>> {
        return quantities.map { quantity -> mapOf("quantity" to quantity) }
    }

    // --- every ---

    @Test
    fun `every matches when all elements satisfy the predicate`() {
        assertTrue(
            actual = evaluate(condition = "every(lineItems[quantity >= 1])", "lineItems" to items(1, 2, 3))
        )
    }

    @Test
    fun `every does not match when one element fails`() {
        assertFalse(
            actual = evaluate(condition = "every(lineItems[quantity >= 1])", "lineItems" to items(1, 0, 3))
        )
    }

    /** REQ-06 asks for empty-collection behaviour to be defined: `every` is vacuously true. */
    @Test
    fun `every is true for an empty collection`() {
        assertTrue(
            actual = evaluate(condition = "every(lineItems[quantity >= 1])", "lineItems" to emptyList<Any>())
        )
    }

    /**
     * A collection the record does not carry is a different question from one it carries holding
     * nothing, and gets a different answer: there is nothing to quantify over, so `every` produces no
     * value and the condition is undecided. It used to answer vacuously true, which told a rule the
     * predicate had been checked against data that never arrived.
     */
    @Test
    fun `every is undecided for a missing collection`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "every(lineItems[quantity >= 1])"),
        )
    }

    // --- any ---

    @Test
    fun `any matches when one element satisfies the predicate`() {
        assertTrue(
            actual = evaluate(
                condition = """any(alerts[severity == "high"])""",
                "alerts" to listOf(mapOf("severity" to "low"), mapOf("severity" to "high"))
            )
        )
    }

    @Test
    fun `any does not match when no element satisfies the predicate`() {
        assertFalse(
            actual = evaluate(
                condition = """any(alerts[severity == "high"])""",
                "alerts" to listOf(mapOf("severity" to "low"))
            )
        )
    }

    /** The counterpart of the `every` rule: nothing satisfies a predicate in an empty collection. */
    @Test
    fun `any is false for an empty collection`() {
        assertFalse(
            actual = evaluate(condition = """any(alerts[severity == "high"])""", "alerts" to emptyList<Any>())
        )
    }

    /** The counterpart of the `every` rule: a collection that never arrived decides nothing. */
    @Test
    fun `any is undecided for a missing collection`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = """any(alerts[severity == "high"])"""),
        )
    }

    // --- composition ---

    @Test
    fun `a predicate works over a sliced collection`() {
        assertTrue(
            actual = evaluate(
                condition = "every(take(lineItems, 2)[quantity >= 1])",
                "lineItems" to items(1, 2, 0)
            ),
            message = "the failing item is outside the first two"
        )
        assertFalse(
            actual = evaluate(
                condition = "every(take(lineItems, 3)[quantity >= 1])",
                "lineItems" to items(1, 2, 0)
            )
        )
    }

    @Test
    fun `a predicate works over an already filtered collection`() {
        assertTrue(
            actual = evaluate(
                condition = "every(lineItems[quantity > 0][quantity < 10])",
                "lineItems" to items(0, 1, 2)
            ),
            message = "the zero is removed by the first filter, so the rest are all under ten"
        )
    }

    @Test
    fun `a predicate may compare against a document field`() {
        assertTrue(
            actual = evaluate(
                condition = "every(lineItems[quantity >= threshold])",
                "lineItems" to items(5, 6),
                "threshold" to 5
            )
        )
        assertFalse(
            actual = evaluate(
                condition = "every(lineItems[quantity >= threshold])",
                "lineItems" to items(5, 6),
                "threshold" to 6
            )
        )
    }

    @Test
    fun `a predicate combines with ordinary boolean logic`() {
        assertTrue(
            actual = evaluate(
                condition = """every(lineItems[quantity >= 1]) and not any(alerts[severity == "high"])""",
                "lineItems" to items(1, 2),
                "alerts" to listOf(mapOf("severity" to "low"))
            )
        )
        assertFalse(
            actual = evaluate(
                condition = """every(lineItems[quantity >= 1]) and not any(alerts[severity == "high"])""",
                "lineItems" to items(1, 2),
                "alerts" to listOf(mapOf("severity" to "high"))
            )
        )
    }

    /** The desugared form must mean the same thing as the bare call. */
    @Test
    fun `an explicit comparison against true behaves like the bare call`() {
        assertTrue(
            actual = evaluate(condition = "every(lineItems[quantity >= 1]) == true", "lineItems" to items(1, 2))
        )
        assertTrue(
            actual = evaluate(condition = "any(lineItems[quantity >= 9]) == false", "lineItems" to items(1, 2))
        )
    }

    // --- validation ---

    @Test
    fun `a predicate without a condition is rejected at validation time`() {
        val error = validate(condition = "every(lineItems)")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
        assertTrue(
            actual = error?.message?.contains(other = "collection with a condition") == true,
            message = "the diagnostic must say what is missing, got: ${error?.message}"
        )
    }

    @Test
    fun `two arguments are rejected at validation time`() {
        val error = validate(condition = "every(lineItems[quantity >= 1], alerts)")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `a well-formed predicate validates without errors`() {
        val errors = validate(condition = "every(lineItems[quantity >= 1])")
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertTrue(actual = errors.isEmpty(), message = "expected no errors, got: $errors")
    }

    private fun evaluate(condition: String, vararg fields: Pair<String, Any?>): Boolean {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }

    /**
     * The condition's own verdict, which [evaluate] cannot show: a rule with no `not_exists` branch
     * reports an undecided condition as no match, exactly as it reports a false one.
     */
    private fun verdict(condition: String, vararg fields: Pair<String, Any?>): ConditionVerdict {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema).single()
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return compiled.expression.evaluate(context = prepared, trace = null)
    }

    private fun validate(condition: String) = Validator.validate(
        asts = Parser(input = rule(condition = condition)).parseRules(),
        schema = schema
    ).diagnostics

    private fun rule(condition: String): String = """
        rule "predicate-test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
