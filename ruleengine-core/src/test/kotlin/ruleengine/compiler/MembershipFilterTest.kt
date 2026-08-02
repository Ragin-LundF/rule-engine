package ruleengine.compiler

import ruleengine.core.domain.dto.NormalizerId
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `element in source` — REQ-01. */
class MembershipFilterTest {

    private val schema = FieldSchema(
        name = "membership-schema",
        fields = mapOf(
            FieldId(value = "priorityCustomerIds") to FieldDefinition(
                id = FieldId(value = "priorityCustomerIds"),
                type = FieldType.STRING_SET,
                normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase"))
            ),
            FieldId(value = "country") to FieldDefinition(
                id = FieldId(value = "country"),
                type = FieldType.TEXT,
                operators = setOf(ruleengine.core.domain.dto.OperatorId(value = "in"))
            ),
            FieldId(value = "invoices") to FieldDefinition(
                id = FieldId(value = "invoices"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "customerId") to FieldDefinition(
                        id = FieldId(value = "customerId"),
                        type = FieldType.TEXT,
                        normalizers = listOf(NormalizerId(value = "trim"), NormalizerId(value = "lowercase"))
                    ),
                    FieldId(value = "amount") to FieldDefinition(
                        id = FieldId(value = "amount"),
                        type = FieldType.DECIMAL
                    )
                )
            ),
            FieldId(value = "watchedCustomers") to FieldDefinition(
                id = FieldId(value = "watchedCustomers"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "id") to FieldDefinition(
                        id = FieldId(value = "id"),
                        type = FieldType.TEXT
                    )
                )
            )
        )
    )

    private val invoices = listOf(
        mapOf("customerId" to "acme", "amount" to 8000),
        mapOf("customerId" to "globex", "amount" to 3000),
        mapOf("customerId" to "acme", "amount" to 4000)
    )

    // --- membership against a string set ---

    @Test
    fun `a filter selects the elements whose member is in the string set`() {
        assertTrue(
            actual = evaluate(
                condition = "sum(invoices[customerId in priorityCustomerIds].amount) > 10000",
                "invoices" to invoices,
                "priorityCustomerIds" to listOf("acme")
            ),
            message = "the two acme invoices total 12000"
        )
    }

    @Test
    fun `a filter does not select elements outside the string set`() {
        assertFalse(
            actual = evaluate(
                condition = "sum(invoices[customerId in priorityCustomerIds].amount) > 10000",
                "invoices" to invoices,
                "priorityCustomerIds" to listOf("globex")
            ),
            message = "globex alone is 3000"
        )
    }

    @Test
    fun `an empty membership source selects nothing`() {
        assertTrue(
            actual = evaluate(
                condition = "count(invoices[customerId in priorityCustomerIds]) == 0",
                "invoices" to invoices,
                "priorityCustomerIds" to emptyList<String>()
            )
        )
    }

    @Test
    fun `a missing membership source selects nothing`() {
        assertTrue(
            actual = evaluate(
                condition = "count(invoices[customerId in priorityCustomerIds]) == 0",
                "invoices" to invoices
            )
        )
    }

    /** A single-value source must behave like a source of one, not collapse into a scalar mismatch. */
    @Test
    fun `a source holding one value still matches`() {
        assertTrue(
            actual = evaluate(
                condition = "count(invoices[customerId in priorityCustomerIds]) == 2",
                "invoices" to invoices,
                "priorityCustomerIds" to listOf("acme")
            )
        )
    }

    /** REQ-01: both sides must be matched under the declared normalizers. */
    @Test
    fun `membership matching applies the declared normalizers to both sides`() {
        assertTrue(
            actual = evaluate(
                condition = "count(invoices[customerId in priorityCustomerIds]) == 1",
                "invoices" to listOf(mapOf("customerId" to "  ACME  ", "amount" to 1)),
                "priorityCustomerIds" to listOf(" Acme ")
            ),
            message = "trim + lowercase are declared on both fields"
        )
    }

    // --- membership against a collection projection ---

    @Test
    fun `the source may be a collection projection`() {
        assertTrue(
            actual = evaluate(
                condition = "count(invoices[customerId in watchedCustomers.id]) == 2",
                "invoices" to invoices,
                "watchedCustomers" to listOf(mapOf("id" to "acme"), mapOf("id" to "initech"))
            )
        )
    }

    @Test
    fun `a collection projection that shares no value selects nothing`() {
        assertFalse(
            actual = evaluate(
                condition = "count(invoices[customerId in watchedCustomers.id]) > 0",
                "invoices" to invoices,
                "watchedCustomers" to listOf(mapOf("id" to "initech"))
            )
        )
    }

    // --- membership against a list variable ---

    @Test
    fun `the source may be a list variable`() {
        val rule = """
            rule "collect" {
              when
                count(invoices) > 0
              then
                add "acme" to watched
            }

            rule "use" {
              when
                count(invoices[customerId in ${'$'}watched]) == 2
              then
                flag "ok"
            }
        """.trimIndent()
        val asts = Parser(input = rule).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of("invoices" to invoices), schema = schema)
        val matched = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared)
            .matches.map { match -> match.ruleId }

        assertTrue(actual = matched.contains(element = "use"), message = "matched: $matched")
    }

    // --- composition ---

    @Test
    fun `membership composes with another filter on the same collection`() {
        assertTrue(
            actual = evaluate(
                condition = "count(invoices[customerId in priorityCustomerIds][amount > 5000]) == 1",
                "invoices" to invoices,
                "priorityCustomerIds" to listOf("acme")
            ),
            message = "of the two acme invoices only one is over 5000"
        )
    }

    // --- routing: the legacy spelling must not change ---

    @Test
    fun `a literal list keeps the legacy condition path`() {
        val condition = Parser(input = rule(condition = """country in ["de", "at"]""")).parseRules()
            .single().condition

        assertTrue(
            actual = condition is ConditionAst,
            message = "a written-out list must stay legacy, so it keeps normalizing every item"
        )
    }

    @Test
    fun `a named source takes the value expression path`() {
        val condition = Parser(input = rule(condition = "country in priorityCustomerIds")).parseRules()
            .single().condition

        assertTrue(actual = condition is ComparisonExpressionAst, message = "got: $condition")
    }

    @Test
    fun `the legacy list membership still evaluates`() {
        assertTrue(actual = evaluate(condition = """country in ["de", "at"]""", "country" to "de"))
        assertFalse(actual = evaluate(condition = """country in ["de", "at"]""", "country" to "fr"))
    }

    // --- validation ---

    @Test
    fun `testing a whole collection for membership is rejected at validation time`() {
        val error = validate(condition = "invoices in priorityCustomerIds")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
        assertTrue(
            actual = error?.message?.contains(other = "single value") == true,
            message = "the diagnostic must say what is wrong, got: ${error?.message}"
        )
    }

    /**
     * A projection across a collection is typed by its leaf, so `invoices.customerId` reads as text
     * and this pairing is not diagnosed. It evaluates to false rather than matching wrongly.
     * Widening the kind lattice to catch it would also reject `invoices.amount > 5`, which is an
     * existing spelling.
     */
    @Test
    fun `a projection on the left is not diagnosed but never matches`() {
        assertFalse(
            actual = evaluate(
                condition = "invoices.customerId in priorityCustomerIds",
                "invoices" to invoices,
                "priorityCustomerIds" to listOf("acme")
            )
        )
    }

    @Test
    fun `a well-formed membership validates without errors`() {
        val errors = validate(condition = "sum(invoices[customerId in priorityCustomerIds].amount) > 1")
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertTrue(actual = errors.isEmpty(), message = "expected no errors, got: $errors")
    }

    private fun evaluate(condition: String, vararg fields: Pair<String, Any?>): Boolean {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun validate(condition: String) = Validator.validate(
        asts = Parser(input = rule(condition = condition)).parseRules(),
        schema = schema
    ).diagnostics

    private fun rule(condition: String): String = """
        rule "membership-test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
