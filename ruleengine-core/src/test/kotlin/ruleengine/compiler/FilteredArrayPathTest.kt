package ruleengine.compiler

import ruleengine.core.domain.FieldDefinition
import ruleengine.core.domain.FieldId
import ruleengine.core.domain.FieldSchema
import ruleengine.core.domain.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilteredArrayPathTest {

    private val schema = FieldSchema(
        name = "test-schema",
        fields = mapOf(
            FieldId(value = "transactions") to FieldDefinition(
                id = FieldId(value = "transactions"),
                type = FieldType.STRING_SET
            )
        )
    )

    private fun evaluate(rule: String, vararg fields: Pair<String, Any?>): Boolean {
        val asts = Parser(input = rule).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val engine = RuleEngine(compiledRules = compiled)
        val ctx = RuleContext.of(*fields)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        val result = engine.evaluate(prepared = prepared)
        return result.matches.isNotEmpty()
    }

    private fun makeRule(condition: String, flag: String = "risk"): String = """
        rule "test" {
          when
            $condition
          then
            flag "$flag"
        }
    """.trimIndent()

    // --- sum with filter ---

    @Test
    fun `sum with label filter - positive case`() {
        val transactions = listOf(
            mapOf("label" to "risk", "amount" to 60),
            mapOf("label" to "risk", "amount" to 50),
            mapOf("label" to "safe", "amount" to 1000)
        )
        assertTrue(
            evaluate(
                makeRule("""sum(transactions[label == "risk"].amount) > 100"""),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `sum with label filter - negative case`() {
        val transactions = listOf(
            mapOf("label" to "risk", "amount" to 10),
            mapOf("label" to "safe", "amount" to 1000)
        )
        assertFalse(
            evaluate(
                makeRule("""sum(transactions[label == "risk"].amount) > 100"""),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `sum with label filter - no matching elements returns false`() {
        val transactions = listOf(
            mapOf("label" to "safe", "amount" to 500),
            mapOf("label" to "safe", "amount" to 600)
        )
        assertFalse(
            evaluate(
                makeRule("""sum(transactions[label == "risk"].amount) > 0"""),
                "transactions" to transactions
            )
        )
    }

    // --- count with filter ---

    @Test
    fun `count with label filter - positive case`() {
        val transactions = listOf(
            mapOf("label" to "risk", "amount" to 10),
            mapOf("label" to "risk", "amount" to 20),
            mapOf("label" to "safe", "amount" to 1000)
        )
        assertTrue(
            evaluate(
                makeRule("""count(transactions[label == "risk"]) > 1"""),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `count with label filter - negative case`() {
        val transactions = listOf(
            mapOf("label" to "risk", "amount" to 10),
            mapOf("label" to "safe", "amount" to 1000)
        )
        assertFalse(
            evaluate(
                makeRule("""count(transactions[label == "risk"]) > 1"""),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `count with numeric filter - positive case`() {
        val transactions = listOf(
            mapOf("label" to "salary", "amount" to 3000),
            mapOf("label" to "risk", "amount" to 100),
            mapOf("label" to "rent", "amount" to -900)
        )
        assertTrue(
            evaluate(
                makeRule("""count(transactions[amount > 0]) > 1"""),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `count with filter - no matching elements`() {
        val transactions = listOf(
            mapOf("label" to "safe", "amount" to 10)
        )
        assertFalse(
            evaluate(
                makeRule("""count(transactions[label == "risk"]) > 0"""),
                "transactions" to transactions
            )
        )
    }
}
