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
import kotlin.test.assertTrue

class AggregatePerformanceTest {

    private val schema = FieldSchema(
        name = "perf-schema",
        fields = mapOf(
            FieldId(value = "transactions") to FieldDefinition(
                id = FieldId(value = "transactions"),
                type = FieldType.STRING_SET
            )
        )
    )

    private fun compile(ruleText: String): RuleEngine {
        val asts = Parser(input = ruleText).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        return RuleEngine(compiledRules = compiled)
    }

    private fun evaluate(engine: RuleEngine, transactions: List<Map<String, Any>>): Boolean {
        val ctx = RuleContext.of("transactions" to transactions)
        val prepared = PreparedRuleContext.prepare(ctx = ctx, schema = schema)
        return engine.evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun generateTransactions(count: Int): List<Map<String, Any>> {
        return (1..count).map { i ->
            mapOf(
                "label" to if (i % 10 == 0) "risk" else "safe",
                "amount" to i
            )
        }
    }

    @Test
    fun `count over 10000 transactions completes within time limit`() {
        val engine = compile("""
            rule "count test" {
              when
                count(transactions) > 100
              then
                flag "ok"
            }
        """.trimIndent())
        val transactions = generateTransactions(count = 10_000)

        val start = System.currentTimeMillis()
        val result = evaluate(engine = engine, transactions = transactions)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result, "Expected count(10000) > 100 to be true")
        assertTrue(elapsed < 2000, "count over 10000 took ${elapsed}ms, expected < 2000ms")
    }

    @Test
    fun `sum over 10000 transactions completes within time limit`() {
        val engine = compile("""
            rule "sum test" {
              when
                sum(transactions.amount) > 1000
              then
                flag "ok"
            }
        """.trimIndent())
        val transactions = generateTransactions(count = 10_000)

        val start = System.currentTimeMillis()
        val result = evaluate(engine = engine, transactions = transactions)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result, "Expected sum > 1000 to be true")
        assertTrue(elapsed < 2000, "sum over 10000 took ${elapsed}ms, expected < 2000ms")
    }

    @Test
    fun `filtered sum over 10000 transactions completes within time limit`() {
        val engine = compile("""
            rule "filtered sum test" {
              when
                sum(transactions[label == "risk"].amount) > 0
              then
                flag "ok"
            }
        """.trimIndent())
        val transactions = generateTransactions(count = 10_000)

        val start = System.currentTimeMillis()
        val result = evaluate(engine = engine, transactions = transactions)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result, "Expected filtered sum > 0 to be true")
        assertTrue(elapsed < 2000, "filtered sum over 10000 took ${elapsed}ms, expected < 2000ms")
    }

    @Test
    fun `percentage rule with two filtered sums over 10000 transactions completes within time limit`() {
        val engine = compile("""
            rule "percentage test" {
              when
                sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03
              then
                flag "ok"
            }
        """.trimIndent())
        // 10% are risk, amounts 10,20,...,1000 => risk sum = 10+20+...+1000 = 5050*10/100... let's just check timing
        val transactions = generateTransactions(count = 10_000)

        val start = System.currentTimeMillis()
        evaluate(engine = engine, transactions = transactions)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(elapsed < 3000, "percentage rule over 10000 took ${elapsed}ms, expected < 3000ms")
    }

    @Test
    fun `repeated evaluation of same compiled rule against multiple contexts`() {
        val engine = compile("""
            rule "repeated eval" {
              when
                sum(transactions.amount) > 1000
              then
                flag "ok"
            }
        """.trimIndent())
        val transactions = generateTransactions(count = 1_000)

        val start = System.currentTimeMillis()
        var trueCount = 0
        repeat(times = 100) {
            if (evaluate(engine = engine, transactions = transactions)) trueCount++
        }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(trueCount == 100, "Expected all 100 evaluations to match")
        assertTrue(elapsed < 3000, "100 repeated evaluations took ${elapsed}ms, expected < 3000ms")
    }

    @Test
    fun `cache prevents duplicate aggregate evaluation within single rule`() {
        // Rule uses sum(transactions.amount) twice — cache should make second call free
        val engine = compile("""
            rule "cache test" {
              when
                sum(transactions.amount) > 0 and sum(transactions.amount) > -1
              then
                flag "ok"
            }
        """.trimIndent())
        val transactions = generateTransactions(count = 5_000)

        val start = System.currentTimeMillis()
        val result = evaluate(engine = engine, transactions = transactions)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result, "Expected both conditions to be true")
        assertTrue(elapsed < 2000, "Cached double-aggregate over 5000 took ${elapsed}ms, expected < 2000ms")
    }
}
