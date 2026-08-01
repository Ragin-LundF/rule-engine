package ruleengine.integration

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of rule output variables: `set` publishes, `$name` reads, and the load-time
 * checks that keep a read from referring to nothing.
 */
class VariableIntegrationTest {

    private val schema = FieldSchema(
        name = "orders",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "customer") to FieldDefinition(
                id = FieldId(value = "customer"),
                type = FieldType.TEXT
            ),
            FieldId(value = "orders") to FieldDefinition(
                id = FieldId(value = "orders"),
                type = FieldType.COLLECTION
            )
        )
    )

    // ── evaluation ────────────────────────────────────────────────────────────

    @Test
    fun `a later rule reads a variable an earlier rule published`() {
        val result = evaluate(
            rules = """
                rule "total" {
                  description "d"
                  when
                    amount > 0
                  then
                    set orderTotal = amount * 3
                }
                rule "vip" {
                  description "d"
                  when
                    ${'$'}orderTotal >= 300
                  then
                    label "vip"
                }
            """.trimIndent(),
            "amount" to 100
        )

        assertEquals(expected = listOf("total", "vip"), actual = result.matches.map { it.ruleId })
        assertEquals(expected = BigDecimal("300"), actual = result.variables["orderTotal"])
    }

    @Test
    fun `reading a variable no matching rule assigned makes the condition false`() {
        val result = evaluate(
            rules = """
                rule "total" {
                  description "d"
                  when
                    amount > 1000
                  then
                    set orderTotal = amount
                }
                rule "vip" {
                  description "d"
                  when
                    ${'$'}orderTotal >= 0
                  then
                    label "vip"
                }
            """.trimIndent(),
            "amount" to 100
        )

        assertEquals(expected = emptyList(), actual = result.matches.map { it.ruleId })
        assertTrue(actual = result.variables.isEmpty())
    }

    @Test
    fun `an action of the assigning rule already sees the variable`() {
        val result = evaluate(
            rules = """
                rule "total" {
                  description "d"
                  when
                    amount > 0
                  then
                    set doubled = amount * 2
                    label ${'$'}doubled
                }
            """.trimIndent(),
            "amount" to 21
        )

        assertEquals(expected = listOf(BigDecimal("42")), actual = result.matches[0].actions[0].arguments)
        assertEquals(expected = mapOf("doubled" to BigDecimal("42")), actual = result.matches[0].assignments)
    }

    @Test
    fun `the last matching rule wins when two rules assign the same variable`() {
        val result = evaluate(
            rules = """
                rule "first" {
                  description "d"
                  when
                    amount > 0
                  then
                    set tier = "bronze"
                }
                rule "second" {
                  description "d"
                  when
                    amount > 10
                  then
                    set tier = "gold"
                }
            """.trimIndent(),
            "amount" to 50
        )

        assertEquals(expected = "gold", actual = result.variables["tier"])
        assertEquals(expected = mapOf("tier" to "bronze"), actual = result.matches[0].assignments)
        assertEquals(expected = mapOf("tier" to "gold"), actual = result.matches[1].assignments)
    }

    @Test
    fun `a variable read inside a filter predicate sees the same value`() {
        val result = evaluate(
            rules = """
                rule "threshold" {
                  description "d"
                  when
                    amount > 0
                  then
                    set threshold = 15
                }
                rule "large-orders" {
                  description "d"
                  when
                    count(orders[amount > ${'$'}threshold]) >= 2
                  then
                    label "bulk"
                }
            """.trimIndent(),
            "amount" to 1,
            "orders" to listOf(mapOf("amount" to 10), mapOf("amount" to 20), mapOf("amount" to 30))
        )

        assertEquals(expected = listOf("threshold", "large-orders"), actual = result.matches.map { it.ruleId })
    }

    @Test
    fun `a reused context does not carry variables into the next evaluation`() {
        val rules = """
            rule "total" {
              description "d"
              when
                amount > 1000
              then
                set big = amount
            }
            rule "reads" {
              description "d"
              when
                ${'$'}big >= 0
              then
                label "seen"
            }
        """.trimIndent()
        val engine = RuleEngine(compiledRules = compile(rules = rules))

        val matching = prepare("amount" to 5000)
        assertEquals(expected = 2, actual = engine.evaluate(prepared = matching).matches.size)

        // Same context object, evaluated again: the previous run's variable must not survive.
        val second = engine.evaluate(prepared = matching)
        assertEquals(expected = 2, actual = second.matches.size)

        val nonMatching = prepare("amount" to 1)
        val third = engine.evaluate(prepared = nonMatching)
        assertEquals(expected = emptyList(), actual = third.matches.map { it.ruleId })
        assertNull(actual = third.variables["big"])
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Test
    fun `a forward reference is an error`() {
        val errors = errorsOf(
            rules = """
                rule "reads" {
                  description "d"
                  when
                    ${'$'}orderTotal >= 1
                  then
                    label "x"
                }
                rule "writes" {
                  description "d"
                  when
                    amount > 0
                  then
                    set orderTotal = amount
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "unknown variable '\$orderTotal'")
    }

    @Test
    fun `a typo suggests the closest defined variable`() {
        val errors = errorsOf(
            rules = """
                rule "writes" {
                  description "d"
                  when
                    amount > 0
                  then
                    set orderTotal = amount
                }
                rule "reads" {
                  description "d"
                  when
                    ${'$'}orderTotl >= 1
                  then
                    label "x"
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertEquals(expected = "Did you mean '\$orderTotal'?", actual = errors[0].suggestion)
    }

    @Test
    fun `a variable named like a schema field is an error`() {
        val errors = errorsOf(
            rules = """
                rule "writes" {
                  description "d"
                  when
                    amount > 0
                  then
                    set amount = 1
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "also the name of a schema field")
    }

    @Test
    fun `assigning the same variable twice is a warning, not an error`() {
        val diagnostics = validate(
            rules = """
                rule "first" {
                  description "d"
                  when
                    amount > 0
                  then
                    set tier = "bronze"
                }
                rule "second" {
                  description "d"
                  when
                    amount > 10
                  then
                    set tier = "gold"
                }
            """.trimIndent()
        )

        assertTrue(actual = diagnostics.none { it.severity == Severity.ERROR })
        val warning = diagnostics.single { it.message.contains(other = "is assigned by rule") }
        assertEquals(expected = Severity.WARNING, actual = warning.severity)
    }

    @Test
    fun `an unknown field inside a set expression is reported`() {
        val errors = errorsOf(
            rules = """
                rule "writes" {
                  description "d"
                  when
                    amount > 0
                  then
                    set total = amoutn
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "Unknown field 'amoutn'")
    }

    @Test
    fun `a set clause reading a variable set earlier in the same rule is valid`() {
        val diagnostics = validate(
            rules = """
                rule "chained" {
                  description "d"
                  when
                    amount > 0
                  then
                    set base = amount
                    set doubled = ${'$'}base * 2
                }
            """.trimIndent()
        )

        assertTrue(
            actual = diagnostics.none { it.severity == Severity.ERROR },
            message = "unexpected errors: $diagnostics"
        )
    }

    @Test
    fun `a set clause reading itself before assignment is an error`() {
        val errors = errorsOf(
            rules = """
                rule "self" {
                  description "d"
                  when
                    amount > 0
                  then
                    set total = ${'$'}total + amount
                }
            """.trimIndent()
        )

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors[0].message, other = "unknown variable '\$total'")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun compile(rules: String) =
        Compiler.compileRules(asts = Parser(input = rules).parseRules(), schema = schema)

    private fun prepare(vararg fields: Pair<String, Any?>) =
        PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)

    private fun evaluate(rules: String, vararg fields: Pair<String, Any?>): EvaluationResult =
        RuleEngine(compiledRules = compile(rules = rules)).evaluate(prepared = prepare(*fields))

    private fun validate(rules: String): List<ValidationDiagnostic> =
        Validator.validate(asts = Parser(input = rules).parseRules(), schema = schema).diagnostics

    private fun errorsOf(rules: String): List<ValidationDiagnostic> =
        validate(rules = rules).filter { it.severity == Severity.ERROR }
}
