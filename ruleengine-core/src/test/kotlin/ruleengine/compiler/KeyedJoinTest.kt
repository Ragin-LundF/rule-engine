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

/** `sumByKey(key, source, source, ...)` — REQ-03. */
class KeyedJoinTest {

    private fun monthly(keyType: FieldType = FieldType.TEXT, valueType: FieldType = FieldType.DECIMAL) =
        FieldDefinition(
            id = FieldId(value = "byMonth"),
            type = FieldType.COLLECTION,
            fields = mapOf(
                FieldId(value = "month") to FieldDefinition(id = FieldId(value = "month"), type = keyType),
                FieldId(value = "amount") to FieldDefinition(id = FieldId(value = "amount"), type = valueType)
            )
        )

    private val schema = FieldSchema(
        name = "join-schema",
        fields = mapOf(
            FieldId(value = "sales") to monthly().copy(id = FieldId(value = "sales")),
            FieldId(value = "refunds") to monthly().copy(id = FieldId(value = "refunds")),
            FieldId(value = "fees") to monthly(keyType = FieldType.INTEGER).copy(id = FieldId(value = "fees")),
            FieldId(value = "notes") to FieldDefinition(
                id = FieldId(value = "notes"),
                type = FieldType.COLLECTION,
                fields = mapOf(
                    FieldId(value = "text") to FieldDefinition(
                        id = FieldId(value = "text"),
                        type = FieldType.TEXT
                    )
                )
            )
        )
    )

    private fun rows(vararg pairs: Pair<String, Int>): List<Map<String, Any?>> {
        return pairs.map { (month, amount) -> mapOf("month" to month, "amount" to amount) }
    }

    private val condition = """min(sumByKey("month", sales.amount, refunds.amount)) >= 0"""

    @Test
    fun `every key nets out positive`() {
        assertTrue(
            actual = evaluate(
                condition = condition,
                "sales" to rows("2024-01" to 500, "2024-02" to 300),
                "refunds" to rows("2024-01" to -200, "2024-02" to -100)
            )
        )
    }

    @Test
    fun `a key that nets out negative does not match`() {
        assertFalse(
            actual = evaluate(
                condition = condition,
                "sales" to rows("2024-01" to 500, "2024-02" to 100),
                "refunds" to rows("2024-01" to -200, "2024-02" to -400)
            ),
            message = "February nets to -300"
        )
    }

    /** Outer join: a key only one source mentions still appears, the other contributing zero. */
    @Test
    fun `a key missing from one source still appears`() {
        assertTrue(
            actual = evaluate(
                condition = """count(sumByKey("month", sales.amount, refunds.amount)) == 3""",
                "sales" to rows("2024-01" to 500, "2024-02" to 300),
                "refunds" to rows("2024-03" to -100)
            )
        )
        assertFalse(
            actual = evaluate(
                condition = condition,
                "sales" to rows("2024-01" to 500),
                "refunds" to rows("2024-03" to -100)
            ),
            message = "March has only the refund, so it nets to -100"
        )
    }

    /** Duplicate keys inside one source are summed, so the function preserves the overall total. */
    @Test
    fun `duplicate keys within a source are summed`() {
        assertTrue(
            actual = evaluate(
                condition = """sum(sumByKey("month", sales.amount, refunds.amount)) == 400""",
                "sales" to rows("2024-01" to 300, "2024-01" to 200),
                "refunds" to rows("2024-01" to -100)
            )
        )
        assertTrue(
            actual = evaluate(
                condition = """count(sumByKey("month", sales.amount, refunds.amount)) == 1""",
                "sales" to rows("2024-01" to 300, "2024-01" to 200),
                "refunds" to rows("2024-01" to -100)
            ),
            message = "the two January rows collapse into one key"
        )
    }

    @Test
    fun `two empty sources produce no keys`() {
        assertTrue(
            actual = evaluate(
                condition = """count(sumByKey("month", sales.amount, refunds.amount)) == 0""",
                "sales" to emptyList<Any>(),
                "refunds" to emptyList<Any>()
            )
        )
    }

    @Test
    fun `missing sources produce no keys`() {
        assertTrue(
            actual = evaluate(condition = """count(sumByKey("month", sales.amount, refunds.amount)) == 0""")
        )
    }

    @Test
    fun `more than two sources join on the same key`() {
        assertTrue(
            actual = evaluate(
                condition = """sum(sumByKey("month", sales.amount, refunds.amount, sales.amount)) == 900""",
                "sales" to rows("2024-01" to 500),
                "refunds" to rows("2024-01" to -100)
            )
        )
    }

    @Test
    fun `a filtered source joins on what the filter left`() {
        assertTrue(
            actual = evaluate(
                condition = """count(sumByKey("month", sales[amount > 100].amount, refunds.amount)) == 1""",
                "sales" to rows("2024-01" to 500, "2024-02" to 50),
                "refunds" to rows("2024-01" to -100)
            ),
            message = "February is filtered out of sales and absent from refunds"
        )
    }

    @Test
    fun `the result composes with a collection predicate`() {
        assertTrue(
            actual = evaluate(
                condition = """min(sumByKey("month", sales.amount, refunds.amount)) > 100""",
                "sales" to rows("2024-01" to 500, "2024-02" to 300),
                "refunds" to rows("2024-01" to -200, "2024-02" to -100)
            )
        )
    }

    // --- validation ---

    @Test
    fun `a missing key argument is rejected at validation time`() {
        val error = validate(condition = "min(sumByKey(sales.amount, refunds.amount)) >= 0")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `a single source is rejected at validation time`() {
        val error = validate(condition = """min(sumByKey("month", sales.amount)) >= 0""")
            .firstOrNull { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
        assertTrue(
            actual = error?.message?.contains(other = "at least two sources") == true,
            message = "got: ${error?.message}"
        )
    }

    @Test
    fun `a source whose collection does not declare the key is rejected`() {
        val error = validate(condition = """min(sumByKey("month", sales.amount, notes.text)) >= 0""")
            .firstOrNull { diagnostic -> diagnostic.message.contains(other = "does not declare") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `sources whose key types disagree are rejected`() {
        val error = validate(condition = """min(sumByKey("month", sales.amount, fees.amount)) >= 0""")
            .firstOrNull { diagnostic -> diagnostic.message.contains(other = "declare it as") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `a non-numeric value member is rejected`() {
        val error = validate(condition = """min(sumByKey("text", notes.text, notes.text)) >= 0""")
            .firstOrNull { diagnostic -> diagnostic.message.contains(other = "rather than a number") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `a well-formed join validates without errors`() {
        val errors = validate(condition = condition)
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
        rule "join-test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
