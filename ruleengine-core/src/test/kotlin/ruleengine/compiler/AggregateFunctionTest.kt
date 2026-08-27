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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AggregateFunctionTest {

    private val schema = FieldSchema(
        name = "test-schema",
        fields = mapOf(
            FieldId(value = "transactions") to FieldDefinition(
                id = FieldId(value = "transactions"),
                type = FieldType.STRING_SET
            ),
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            )
        )
    )

    private val transactions = listOf(
        mapOf("amount" to 10),
        mapOf("amount" to 20)
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

    private fun makeRule(condition: String, flag: String = "ok"): String = """
        rule "test" {
          when
            $condition
          then
            flag "$flag"
        }
    """.trimIndent()

    // --- count ---

    @Test
    fun `count - matches when transaction count is greater than threshold`() {
        val threeTransactions = listOf(
            mapOf("amount" to 10),
            mapOf("amount" to 20),
            mapOf("amount" to 30)
        )
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "count(transactions) > 2"),
                "transactions" to threeTransactions
            )
        )
    }

    @Test
    fun `count - does not match when transaction count is not greater than threshold`() {
        val result = evaluate(rule = makeRule(condition = "count(transactions) > 2"), "transactions" to transactions)
        assertEquals(expected = false, actual = result)
    }

    @Test
    fun `count - matches when count equals threshold with GTE`() {
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "count(transactions) >= 2"),
                "transactions" to transactions
            )
        )
    }

    /**
     * A missing collection makes the condition undecided, which a rule with no `not_exists` branch
     * reports as no match — the same outcome a false condition gives, which is why nothing that
     * existed before the branch changed behaviour.
     */
    @Test
    fun `count - missing field evaluates to false`() {
        val result = evaluate(rule = makeRule(condition = "count(transactions) > 0"))
        assertEquals(expected = false, actual = result)
    }

    /**
     * A collection that arrived **empty** counts `0`; one that did not arrive counts nothing at all.
     *
     * Keeping the two apart is what makes `count(x) == 0` a true emptiness test: it used to answer
     * `0` for a missing collection too, so the test was also true for a record that never carried one.
     */
    @Test
    fun `count - an empty but present collection counts zero`() {
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "count(transactions) == 0"),
                "transactions" to emptyList<Any>()
            )
        )
        assertEquals(
            expected = false,
            actual = evaluate(rule = makeRule(condition = "count(transactions) == 0")),
            message = "an absent collection is undecided, not a count of zero"
        )
    }

    /**
     * An empty collection sums to `0` however the path is spelled.
     *
     * The two spellings used to disagree: read whole it was an empty array and summed to `0`, but a
     * projection selected nothing, collapsed to the same value an absent field produces, and came out
     * undecided. `sum(x.member)` is the far commoner spelling, so the surprising answer was the one
     * most rules got.
     */
    @Test
    fun `sum - an empty collection sums to zero, read whole or projected`() {
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "sum(transactions) == 0"),
                "transactions" to emptyList<Any>()
            )
        )
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "sum(transactions.amount) == 0"),
                "transactions" to emptyList<Any>()
            ),
            message = "a projection over an empty collection sums to zero, like reading it whole"
        )
    }

    /** A collection that never arrived has no sum — unlike an empty one, which sums to zero. */
    @Test
    fun `sum - a missing collection has no value`() {
        assertEquals(
            expected = false,
            actual = evaluate(rule = makeRule(condition = "sum(transactions.amount) == 0")),
            message = "a missing sum is undecided, which a rule without not_exists reads as false"
        )
    }

    // --- sum ---

    @Test
    fun `sum - matches when sum of projected amounts exceeds threshold`() {
        val bigTransactions = listOf(
            mapOf("amount" to 600),
            mapOf("amount" to 500)
        )
        assertTrue(evaluate(makeRule("sum(transactions.amount) > 1000"), "transactions" to bigTransactions))
    }

    @Test
    fun `sum - does not match when sum is below threshold`() {
        val result = evaluate(makeRule("sum(transactions.amount) > 1000"), "transactions" to transactions)
        assertEquals(expected = false, actual = result)
    }

    @Test
    fun `sum - matches when sum equals threshold with GTE`() {
        val exactTransactions = listOf(
            mapOf("amount" to 600),
            mapOf("amount" to 400)
        )
        assertTrue(evaluate(makeRule("sum(transactions.amount) >= 1000"), "transactions" to exactTransactions))
    }

    @Test
    fun `sum - skips missing amount values`() {
        val mixed = listOf(
            mapOf("amount" to 800),
            mapOf("purpose" to "no amount"),
            mapOf("amount" to 300)
        )
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "sum(transactions.amount) > 1000"),
                "transactions" to mixed
            )
        )
    }

    // --- validation errors ---

    @Test
    fun `unknown function name produces validation error`() {
        val result = Validator.validate(
            asts = Parser(input = makeRule(condition = "unknown_fn(transactions) > 0")).parseRules(),
            schema = schema
        )
        assertFalse(actual = result.isValid, message = "Expected invalid")
        assertTrue(
            actual = result.diagnostics.any { it.message.contains("Unknown function") },
            message = "Expected unknown function error, got: ${result.diagnostics}"
        )
    }

    @Test
    fun `wrong arity produces validation error`() {
        val result = Validator.validate(
            asts = Parser(input = makeRule(condition = "count(transactions, amount) > 0")).parseRules(),
            schema = schema
        )
        assertFalse(actual = result.isValid, message = "Expected invalid")
        assertTrue(
            actual = result.diagnostics.any { it.message.contains("exactly one argument") },
            message = "Expected arity error, got: ${result.diagnostics}"
        )
    }

    // --- subtract ---

    @Test
    fun `subtract - matches when result exceeds threshold`() {
        // subtract([10, 20]) = 10 - 20 = -10 > -15
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "subtract(transactions.amount) > -15"),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `subtract - empty array returns zero`() {
        // subtract([]) = 0, 0 > 5 = false
        assertFalse(
            actual = evaluate(
                rule = makeRule(condition = "subtract(transactions.amount) > 5"),
                "transactions" to emptyList<Map<String, Any>>()
            )
        )
    }

    @Test
    fun `subtract - filtered array`() {
        val data = listOf(
            mapOf("label" to "a", "amount" to 100),
            mapOf("label" to "b", "amount" to 30),
            mapOf("label" to "a", "amount" to 20)
        )
        // subtract(a amounts) = 100 - 20 = 80 > 50
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = """subtract(transactions[label == "a"].amount) > 50"""),
                "transactions" to data
            )
        )
    }

    // --- avg ---

    @Test
    fun `avg - matches when average exceeds threshold`() {
        // avg([10, 20]) = 15 > 12
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "avg(transactions.amount) > 12"),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `avg - empty array returns missing, comparison is false`() {
        assertFalse(
            actual = evaluate(
                rule = makeRule(condition = "avg(transactions.amount) > 0"),
                "transactions" to emptyList<Map<String, Any>>()
            )
        )
    }

    @Test
    fun `avg - filtered array`() {
        val data = listOf(
            mapOf("label" to "risk", "amount" to 100),
            mapOf("label" to "safe", "amount" to 900),
            mapOf("label" to "risk", "amount" to 200)
        )
        // avg(risk amounts) = avg([100, 200]) = 150 > 100
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = """avg(transactions[label == "risk"].amount) > 100"""),
                "transactions" to data
            )
        )
    }

    // --- median ---

    @Test
    fun `median - odd count returns middle element`() {
        val data = listOf(
            mapOf("amount" to 1),
            mapOf("amount" to 5),
            mapOf("amount" to 10)
        )
        // median([1, 5, 10]) = 5 > 4
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "median(transactions.amount) > 4"),
                "transactions" to data
            )
        )
    }

    @Test
    fun `median - even count returns average of two middle elements`() {
        val data = listOf(
            mapOf("amount" to 1),
            mapOf("amount" to 5),
            mapOf("amount" to 10),
            mapOf("amount" to 20)
        )
        // median([1, 5, 10, 20]) = 7.5 > 7
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "median(transactions.amount) > 7"),
                "transactions" to data
            )
        )
    }

    @Test
    fun `median - empty array returns missing, comparison is false`() {
        assertFalse(
            actual = evaluate(
                rule = makeRule(condition = "median(transactions.amount) > 0"),
                "transactions" to emptyList<Map<String, Any>>()
            )
        )
    }

    // --- max ---

    @Test
    fun `max - matches when max exceeds threshold`() {
        // max([10, 20]) = 20 > 15
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "max(transactions.amount) > 15"),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `max - empty array returns missing, comparison is false`() {
        assertFalse(
            actual = evaluate(
                rule = makeRule(condition = "max(transactions.amount) > 0"),
                "transactions" to emptyList<Map<String, Any>>()
            )
        )
    }

    @Test
    fun `max - filtered array`() {
        val data = listOf(
            mapOf("label" to "risk", "amount" to 50),
            mapOf("label" to "safe", "amount" to 900),
            mapOf("label" to "risk", "amount" to 200)
        )
        // max(risk amounts) = 200 > 100
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = """max(transactions[label == "risk"].amount) > 100"""),
                "transactions" to data
            )
        )
    }

    // --- min ---

    @Test
    fun `min - matches when min exceeds threshold`() {
        // min([10, 20]) = 10 > 5
        assertTrue(
            actual = evaluate(
                rule = makeRule(condition = "min(transactions.amount) > 5"),
                "transactions" to transactions
            )
        )
    }

    @Test
    fun `min - empty array returns missing, comparison is false`() {
        assertFalse(
            actual = evaluate(
                rule = makeRule(condition = "min(transactions.amount) > 0"),
                "transactions" to emptyList<Map<String, Any>>()
            )
        )
    }

    @Test
    fun `min - filtered array`() {
        val data = listOf(
            mapOf("label" to "risk", "amount" to 50),
            mapOf("label" to "safe", "amount" to 900),
            mapOf("label" to "risk", "amount" to 200)
        )
        // min(risk amounts) = 50 < 100
        assertFalse(
            actual = evaluate(
                rule = makeRule(condition = """min(transactions[label == "risk"].amount) > 100"""),
                "transactions" to data
            )
        )
    }
}
