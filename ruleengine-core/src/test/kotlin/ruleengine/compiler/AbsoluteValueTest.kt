package ruleengine.compiler

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

/** `abs(value)` — REQ-05. */
class AbsoluteValueTest {

    private val schema = FieldSchema(
        name = "abs-schema",
        fields = mapOf(
            FieldId(value = "balance") to FieldDefinition(
                id = FieldId(value = "balance"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "count") to FieldDefinition(
                id = FieldId(value = "count"),
                type = FieldType.INTEGER
            ),
            FieldId(value = "label") to FieldDefinition(
                id = FieldId(value = "label"),
                type = FieldType.TEXT
            ),
            FieldId(value = "transactions") to FieldDefinition(
                id = FieldId(value = "transactions"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "amount") to FieldDefinition(
                        id = FieldId(value = "amount"),
                        type = FieldType.DECIMAL
                    )
                )
            )
        )
    )

    @Test
    fun `a negative value becomes positive`() {
        assertTrue(actual = evaluate(condition = "abs(balance) > 1000", "balance" to -1500))
    }

    @Test
    fun `a positive value is unchanged`() {
        assertTrue(actual = evaluate(condition = "abs(balance) > 1000", "balance" to 1500))
    }

    @Test
    fun `a value within the threshold does not match`() {
        assertFalse(actual = evaluate(condition = "abs(balance) > 1000", "balance" to -900))
    }

    @Test
    fun `zero is unchanged`() {
        assertTrue(actual = evaluate(condition = "abs(balance) == 0", "balance" to 0))
    }

    /** Decimal precision must survive, so a fractional magnitude still compares exactly. */
    @Test
    fun `decimal precision is preserved`() {
        assertTrue(actual = evaluate(condition = "abs(balance) == 12.34", "balance" to "-12.34"))
    }

    @Test
    fun `integer precision is preserved`() {
        assertTrue(actual = evaluate(condition = "abs(count) == 7", "count" to -7))
    }

    @Test
    fun `it wraps an aggregate`() {
        assertTrue(
            actual = evaluate(
                condition = "abs(sum(transactions.amount)) > 1000",
                "transactions" to listOf(mapOf("amount" to -700), mapOf("amount" to -400))
            ),
            message = "the sum is -1100, whose magnitude clears the threshold"
        )
    }

    @Test
    fun `it wraps an arithmetic expression`() {
        assertTrue(actual = evaluate(condition = "abs(balance - 100) == 150", "balance" to -50))
    }

    @Test
    fun `a missing input produces a missing result`() {
        assertFalse(
            actual = evaluate(condition = "abs(balance) >= 0"),
            message = "a comparison against a missing value is false, not an exception"
        )
    }

    // --- validation ---

    @Test
    fun `a text argument is rejected at validation time`() {
        val error = validate(condition = "abs(label) > 1")
            .firstOrNull { diagnostic -> diagnostic.message.contains(other = "abs()") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `two arguments are rejected at validation time`() {
        val error = validate(condition = "abs(balance, count) > 1")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
        assertTrue(
            actual = error?.message?.contains(other = "exactly one argument") == true,
            message = "the diagnostic must state the expected arity, got: ${error?.message}"
        )
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
        rule "abs-test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
