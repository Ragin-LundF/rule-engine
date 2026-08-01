package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComparisonExpressionCompilerTest {

    private val schema = FieldSchema(
        name = "test-schema",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "fee") to FieldDefinition(
                id = FieldId(value = "fee"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "name") to FieldDefinition(
                id = FieldId(value = "name"),
                type = FieldType.TEXT
            )
        )
    )

    private fun evaluate(rule: String, vararg fields: Pair<String, Any>): Boolean {
        val asts = Parser(input = rule).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val engine = RuleEngine(compiledRules = compiled)
        val ctx = RuleContext.of(*fields)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared = prepared)
        return result.matches.isNotEmpty()
    }

    private fun makeRule(condition: String): String = """
        rule "test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()

    // --- basic numeric comparison ---

    @Test
    fun `basic numeric GT - matches when amount is greater`() {
        assertTrue(evaluate(makeRule("amount > 100"), "amount" to "150"))
    }

    @Test
    fun `basic numeric GT - does not match when amount is equal`() {
        val result = evaluate(makeRule("amount > 100"), "amount" to "100")
        assertEquals(expected = false, actual = result)
    }

    @Test
    fun `basic numeric GT - does not match when amount is less`() {
        val result = evaluate(makeRule("amount > 100"), "amount" to "50")
        assertEquals(expected = false, actual = result)
    }

    @Test
    fun `basic numeric LTE - matches when amount equals threshold`() {
        assertTrue(evaluate(makeRule("amount <= 100"), "amount" to "100"))
    }

    @Test
    fun `basic numeric EQ - matches when values are equal`() {
        assertTrue(evaluate(makeRule("amount == 200"), "amount" to "200"))
    }

    @Test
    fun `basic numeric NEQ - matches when values differ`() {
        assertTrue(evaluate(makeRule("amount != 200"), "amount" to "300"))
    }

    // --- arithmetic ---

    @Test
    fun `arithmetic - amount plus fee times 2 lte 100 - matches`() {
        // amount=10, fee=20 => 10 + 20*2 = 50 <= 100
        assertTrue(evaluate(makeRule("amount + fee * 2 <= 100"), "amount" to "10", "fee" to "20"))
    }

    @Test
    fun `arithmetic - amount plus fee times 2 lte 100 - does not match`() {
        // amount=50, fee=30 => 50 + 30*2 = 110 > 100
        val result = evaluate(makeRule("amount + fee * 2 <= 100"), "amount" to "50", "fee" to "30")
        assertEquals(expected = false, actual = result)
    }

    @Test
    fun `arithmetic - parenthesized addition then multiply`() {
        // (amount + fee) * 2 <= 100 => (10+10)*2 = 40 <= 100
        assertTrue(evaluate(makeRule("(amount + fee) * 2 <= 100"), "amount" to "10", "fee" to "10"))
    }

    @Test
    fun `arithmetic - parenthesized addition then multiply - does not match`() {
        // (amount + fee) * 2 <= 100 => (40+20)*2 = 120 > 100
        val result = evaluate(makeRule("(amount + fee) * 2 <= 100"), "amount" to "40", "fee" to "20")
        assertEquals(expected = false, actual = result)
    }

    // --- missing field returns false ---

    @Test
    fun `missing field evaluates to false`() {
        val result = evaluate(makeRule("amount > 100"))
        assertEquals(expected = false, actual = result)
    }
}
