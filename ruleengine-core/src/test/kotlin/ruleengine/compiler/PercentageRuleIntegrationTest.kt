package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PercentageRuleIntegrationTest {

    private val schema = FieldSchema(
        name = "test-schema",
        fields = mapOf(
            FieldId(value = "transactions") to FieldDefinition(
                id = FieldId(value = "transactions"),
                type = FieldType.STRING_SET
            )
        )
    )

    private val rule = """
        rule "risk volume exceeds income threshold" {
          when
            sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03
          then
            flag "risk"
        }
    """.trimIndent()

    private fun evaluate(vararg fields: Pair<String, Any?>): Boolean {
        val asts = Parser(input = rule).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val engine = RuleEngine(compiledRules = compiled)
        val ctx = RuleContext.of(*fields)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared = prepared)
        return result.matches.isNotEmpty()
    }

    @Test
    fun `positive case - risk sum 120 exceeds 3 percent of 3120`() {
        // risk sum = 120, positive income sum = 3120, threshold = 93.60 => 120 > 93.60 = true
        val transactions = listOf(
            mapOf("label" to "salary", "amount" to 3000),
            mapOf("label" to "risk", "amount" to 100),
            mapOf("label" to "risk", "amount" to 20),
            mapOf("label" to "rent", "amount" to -900)
        )
        assertTrue(evaluate("transactions" to transactions))
    }

    @Test
    fun `negative case - risk sum 50 does not exceed 3 percent of 3050`() {
        // risk sum = 50, positive income sum = 3050, threshold = 91.50 => 50 > 91.50 = false
        val transactions = listOf(
            mapOf("label" to "salary", "amount" to 3000),
            mapOf("label" to "risk", "amount" to 50),
            mapOf("label" to "rent", "amount" to -900)
        )
        assertFalse(evaluate("transactions" to transactions))
    }

    @Test
    fun `boundary case - risk sum exactly equals threshold returns false`() {
        // risk sum = 90, positive income sum = 3000, threshold = 90.00 => 90 > 90 = false
        val transactions = listOf(
            mapOf("label" to "salary", "amount" to 3000),
            mapOf("label" to "risk", "amount" to 90)
        )
        assertFalse(evaluate("transactions" to transactions))
    }

    @Test
    fun `no risk transactions returns false`() {
        val transactions = listOf(
            mapOf("label" to "salary", "amount" to 3000),
            mapOf("label" to "rent", "amount" to -900)
        )
        assertFalse(evaluate("transactions" to transactions))
    }

    @Test
    fun `all transactions are risk - large risk sum exceeds threshold`() {
        // risk sum = 500, positive income sum = 500, threshold = 15 => 500 > 15 = true
        val transactions = listOf(
            mapOf("label" to "risk", "amount" to 200),
            mapOf("label" to "risk", "amount" to 300)
        )
        assertTrue(evaluate("transactions" to transactions))
    }
}
