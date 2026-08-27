package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What every function answers for every shape its argument can arrive in.
 *
 * One table, four input shapes per function:
 *
 * | shape | the record carries |
 * |---|---|
 * | **absent** | no `orders` key at all |
 * | **empty** | `orders: []` |
 * | **single** | one element |
 * | **several** | two elements |
 *
 * Each is asserted twice where the spelling can differ — `count(orders)` reading the collection whole
 * and `count(orders.amount)` projecting a member out of it. That pairing is the point of the class:
 * the two spellings answered differently for an empty collection and nothing anywhere said so,
 * because no test had ever put them side by side.
 *
 * Values are read through a `set`, so the assertion is on the value the function produced rather than
 * on a comparison over it — `null` here is `MissingExpressionValue`, i.e. an operand that makes any
 * comparison undecided.
 *
 * The governing rule this table encodes: **a missing value propagates as undecided through every
 * expression; `isAvailable` and `isEmpty` are the only two that consume it.**
 */
class AggregateSemanticsTest {

    // ── reductions over numbers ───────────────────────────────────────────────

    @Test
    fun `count`() {
        assertShapes(
            expression = "count(orders)",
            absent = null,
            empty = BigDecimal.ZERO,
            single = BigDecimal.ONE,
            several = BigDecimal(2),
        )
        assertShapes(
            expression = "count(orders.amount)",
            absent = null,
            empty = BigDecimal.ZERO,
            single = BigDecimal.ONE,
            several = BigDecimal(2),
        )
    }

    @Test
    fun `sum`() {
        assertShapes(
            expression = "sum(orders.amount)",
            absent = null,
            empty = BigDecimal.ZERO,
            single = BigDecimal(5),
            several = BigDecimal(12),
        )
    }

    @Test
    fun `subtract`() {
        assertShapes(
            expression = "subtract(orders.amount)",
            absent = null,
            empty = BigDecimal.ZERO,
            single = BigDecimal(5),
            several = BigDecimal(-2),
        )
    }

    /** The average of nothing is undefined, so an empty collection is undecided here, not zero. */
    @Test
    fun `avg`() {
        assertShapes(
            expression = "avg(orders.amount)",
            absent = null,
            empty = null,
            single = BigDecimal(5),
            several = BigDecimal(6),
        )
    }

    @Test
    fun `median`() {
        assertShapes(
            expression = "median(orders.amount)",
            absent = null,
            empty = null,
            single = BigDecimal(5),
            several = BigDecimal(6),
        )
    }

    @Test
    fun `max`() {
        assertShapes(
            expression = "max(orders.amount)",
            absent = null,
            empty = null,
            single = BigDecimal(5),
            several = BigDecimal(7),
        )
    }

    @Test
    fun `min`() {
        assertShapes(
            expression = "min(orders.amount)",
            absent = null,
            empty = null,
            single = BigDecimal(5),
            several = BigDecimal(5),
        )
    }

    // ── predicates over the collection's shape ────────────────────────────────

    /** Vacuously true over an empty collection — but a collection that never arrived is undecided. */
    @Test
    fun `every`() {
        assertShapes(
            expression = "every(orders[amount > 0])",
            absent = null,
            empty = true,
            single = true,
            several = true,
        )
        assertShapes(
            expression = "every(orders[amount > 6])",
            absent = null,
            empty = true,
            single = false,
            several = false,
        )
    }

    @Test
    fun `any`() {
        assertShapes(
            expression = "any(orders[amount > 0])",
            absent = null,
            empty = false,
            single = true,
            several = true,
        )
        assertShapes(
            expression = "any(orders[amount > 6])",
            absent = null,
            empty = false,
            single = false,
            several = true,
        )
    }

    // ── the two that consume a missing value ──────────────────────────────────

    /**
     * The exceptions to the rule, and the reason the rule is safe: these two answer a plain boolean
     * for every shape, so a rule can always ask about availability without the question itself
     * becoming undecidable.
     */
    @Test
    fun `isAvailable`() {
        assertShapes(
            expression = "isAvailable(orders)",
            absent = false,
            empty = false,
            single = true,
            several = true,
        )
        assertShapes(
            expression = "isAvailable(orders.amount)",
            absent = false,
            empty = false,
            single = true,
            several = true,
        )
    }

    @Test
    fun `isEmpty`() {
        assertShapes(
            expression = "isEmpty(orders)",
            absent = false,
            empty = true,
            single = false,
            several = false,
        )
        assertShapes(
            expression = "isEmpty(orders.amount)",
            absent = false,
            empty = true,
            single = false,
            several = false,
        )
    }

    // ── the harness ───────────────────────────────────────────────────────────

    private fun assertShapes(
        expression: String,
        absent: Any?,
        empty: Any?,
        single: Any?,
        several: Any?,
    ) {
        assertEquals(expected = absent, actual = valueOf(expression, ABSENT), message = "$expression / absent")
        assertEquals(expected = empty, actual = valueOf(expression, EMPTY), message = "$expression / empty")
        assertEquals(expected = single, actual = valueOf(expression, SINGLE), message = "$expression / single")
        assertEquals(expected = several, actual = valueOf(expression, SEVERAL), message = "$expression / several")
    }

    /**
     * The value [expression] produced, or null when it produced none.
     *
     * Read through a published variable rather than through a comparison, so a missing result is
     * distinguishable from a zero — a comparison would collapse both to "did not match".
     * `BigDecimal` results are re-scaled so `0` and `0.0000000000` compare equal.
     */
    private fun valueOf(expression: String, fields: Array<Pair<String, Any?>>): Any? {
        val rule = """
            rule "shape" {
              description "reads one value out"
              when
                gate equals true
              then
                set result = $expression
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = SCHEMA)
        val prepared = PreparedRuleContext.prepare(
            ctx = RuleContext.of(*(fields + ("gate" to true))),
            schema = SCHEMA,
        )
        val value = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).variables["result"]
        return if (value is BigDecimal) value.stripTrailingZeros() else value
    }

    private companion object {
        val SCHEMA = FieldSchema(
            name = "aggregate-shapes",
            fields = mapOf(
                FieldId(value = "orders") to FieldDefinition(
                    id = FieldId(value = "orders"),
                    type = FieldType.COLLECTION,
                    fields = mapOf(
                        FieldId(value = "amount") to FieldDefinition(
                            id = FieldId(value = "amount"),
                            type = FieldType.DECIMAL,
                        )
                    )
                ),
                FieldId(value = "gate") to FieldDefinition(
                    id = FieldId(value = "gate"),
                    type = FieldType.BOOLEAN,
                ),
            )
        )

        val ABSENT: Array<Pair<String, Any?>> = arrayOf()
        val EMPTY: Array<Pair<String, Any?>> = arrayOf("orders" to emptyList<Any>())
        val SINGLE: Array<Pair<String, Any?>> = arrayOf("orders" to listOf(mapOf("amount" to 5)))
        val SEVERAL: Array<Pair<String, Any?>> =
            arrayOf("orders" to listOf(mapOf("amount" to 5), mapOf("amount" to 7)))
    }
}
