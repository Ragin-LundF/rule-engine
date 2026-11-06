package ruleengine.evaluator.compiled

import ruleengine.compiler.Compiler
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import ruleengine.jackson.JacksonUtil
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A field the record carries but whose value cannot be read as its declared type is undecided.
 *
 * `RULE-SPEC.md` lists this as one of the sources of an undecided condition, alongside an absent
 * field and an unpublished variable — but only the other two were covered. `PreparedRuleContext`
 * simply does not prepare a value it cannot read, and every leaf answers `UNKNOWN` when its prepared
 * value is absent, so the two arrive at the same verdict by different routes.
 */
class UnreadableValueTest {

    private val schema = FieldSchema(
        name = "unreadable-schema",
        fields = listOf(
            FieldId(value = "count") to FieldType.INTEGER,
            FieldId(value = "amount") to FieldType.DECIMAL,
            FieldId(value = "active") to FieldType.BOOLEAN,
            FieldId(value = "openedAt") to FieldType.DATE,
            FieldId(value = "tags") to FieldType.STRING_SET,
            FieldId(value = "name") to FieldType.TEXT,
        ).associate { (id, type) -> id to FieldDefinition(id = id, type = type) } + lines()
    )

    private fun lines(): Pair<FieldId, FieldDefinition> {
        val id = FieldId(value = "lines")
        return id to FieldDefinition(
            id = id,
            type = FieldType.COLLECTION,
            fields = listOf("period" to FieldType.TEXT, "amount" to FieldType.DECIMAL).associate { (name, type) ->
                FieldId(value = name) to FieldDefinition(id = FieldId(value = name), type = type)
            }
        )
    }

    @Test
    fun `text where an integer is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "count > 1", fields = arrayOf("count" to "not-a-number")),
            message = "the field is present, but nothing about it can decide the comparison"
        )
    }

    @Test
    fun `text where a decimal is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "amount > 1", fields = arrayOf("amount" to "twelve")),
        )
    }

    @Test
    fun `a non-boolean where a boolean is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "active equals true", fields = arrayOf("active" to "yes")),
            message = "only true/false and their string spellings are readable as a boolean"
        )
    }

    @Test
    fun `an unparseable date is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = """openedAt > "2020-01-01"""",
                fields = arrayOf("openedAt" to "the third of May")
            ),
        )
    }

    @Test
    fun `a scalar where a string set is declared is undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = """tags containsAny ["a"]""",
                fields = arrayOf("tags" to 42)
            ),
        )
    }

    // ── numbers that are not readable as their declared type ──────────────────

    @Test
    fun `a fraction where an integer is declared is undecided rather than truncated`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "count == 17", fields = arrayOf("count" to 17.9)),
        )
    }

    @Test
    fun `an integer beyond the long range is undecided rather than wrapped`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(
                condition = "count < 0",
                fields = arrayOf("count" to BigInteger("9223372036854775808"))
            ),
            message = "toLong() wrapped Long.MAX + 1 to a negative number"
        )
    }

    /** JSON `1e400` overflows a double, so Jackson hands over positive infinity. */
    @Test
    fun `a decimal that overflowed to infinity in JSON is undecided instead of failing`() {
        val record = json(text = """{"amount": 1e400, "lines": [{"period": "P1", "amount": 1e400}]}""")

        assertEquals(expected = Double.POSITIVE_INFINITY, actual = record["amount"])
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "amount > 1", fields = record.toList().toTypedArray()),
        )
    }

    @Test
    fun `an infinite collection element is skipped by aggregates and keyed sums instead of failing`() {
        val record = json(
            text = """{"lines": [{"period": "P1", "amount": 1e400}, {"period": "P1", "amount": 5}]}"""
        ).toList().toTypedArray()

        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "sum(lines.amount) == 5", fields = record),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(
                condition = """count(sumByKey("period", lines.amount, lines.amount)) == 1""",
                fields = record
            ),
        )
    }

    /**
     * T26: a filter that selects exactly one element with an unreadable amount is skipped by the
     * aggregates, as the same element already was next to others — before 1.15.0 `sum` was missing.
     */
    @Test
    fun `an aggregate skips the only selected element when its amount is unreadable`() {
        val record = arrayOf<Pair<String, Any?>>(
            "lines" to listOf(mapOf("period" to "P1", "amount" to "abc"), mapOf("period" to "P2", "amount" to 7))
        )
        val expectations = mapOf(
            """sum(lines[period == "P1"].amount) == 0""" to ConditionVerdict.TRUE,
            """subtract(lines[period == "P1"].amount) == 0""" to ConditionVerdict.TRUE,
            """count(lines[period == "P1"].amount) == 1""" to ConditionVerdict.TRUE,
            """avg(lines[period == "P1"].amount) == 0""" to ConditionVerdict.UNKNOWN,
            """median(lines[period == "P1"].amount) == 0""" to ConditionVerdict.UNKNOWN,
            """max(lines[period == "P1"].amount) == 0""" to ConditionVerdict.UNKNOWN,
            """min(lines[period == "P1"].amount) == 0""" to ConditionVerdict.UNKNOWN,
            """sum(lines[period == "P2"].amount) == 7""" to ConditionVerdict.TRUE,
        )

        expectations.forEach { (condition, expected) ->
            assertEquals(
                expected = expected,
                actual = verdict(condition = condition, fields = record),
                message = condition,
            )
        }
    }

    /** T26: a plain comparison against the single value, and an aggregate over a declared scalar, are unchanged. */
    @Test
    fun `a comparison with the single unreadable value and a sum over an unreadable scalar stay as before`() {
        val record = arrayOf<Pair<String, Any?>>(
            "lines" to listOf(mapOf("period" to "P1", "amount" to "abc")),
            "amount" to "abc",
        )

        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = """lines[period == "P1"].amount > 5""", fields = record),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "sum(amount) == 0", fields = record),
        )
    }

    @Test
    fun `a structure where text is declared is undecided rather than its toString`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = """name != "x"""", fields = arrayOf("name" to mapOf("first" to "Ann"))),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = """name != "x"""", fields = arrayOf("name" to listOf("Ann"))),
        )
    }

    /**
     * `1e999999999` parses in no time, but `amount + 1` would expand it to a billion digits. Without
     * the exponent bound this test does not fail, it hangs.
     */
    @Test
    fun `a decimal with an absurd exponent is undecided and never expanded`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "amount + 1 > 0", fields = arrayOf("amount" to "1e999999999")),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "amount + 1 > 0", fields = arrayOf("amount" to BigDecimal("1e-999999999"))),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(
                condition = "sum(lines.amount) + 1 == 6",
                fields = arrayOf(
                    "lines" to listOf(
                        mapOf("period" to "P1", "amount" to BigDecimal("1e999999999")),
                        mapOf("period" to "P1", "amount" to 5),
                    )
                )
            ),
        )
    }

    // ── a readable value of the same shape still decides ──────────────────────

    @Test
    fun `a whole-valued double or a large exponent within bounds where an integer is declared still decides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count == 17", fields = arrayOf("count" to 17.0)),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "amount > 1", fields = arrayOf("amount" to "1e1000")),
        )
    }

    @Test
    fun `a numeric string where an integer is declared still decides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count > 1", fields = arrayOf("count" to "5")),
            message = "a string that reads as a number is readable, and must not be swept up"
        )
    }

    @Test
    fun `a readable boolean string still decides`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "active equals true", fields = arrayOf("active" to "true")),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun json(text: String): Map<String, Any?> {
        return JacksonUtil.jsonMapper.readValue(text, Map::class.java) as Map<String, Any?>
    }

    private fun verdict(condition: String, fields: Array<Pair<String, Any?>>): ConditionVerdict {
        val rule = """
            rule "unreadable-test" {
              description "unreadable values are undecided"
              when
                $condition
              then
                flag "ok"

              not_exists
                flag "undecided"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema).single()
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return compiled.expression.evaluate(context = prepared, trace = null)
    }
}
