package ruleengine.compiler

import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.CompilationException
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `joinByKey("period", credit: credits.amount, debit: debits.amount)` — rows per key across
 * collections, filtered and counted like any other collection.
 *
 * The cases are the re-req's examples E1–E13: a period is positive when `credit > debit`, and the
 * check passes when the positive periods are at least half of `periods`.
 */
class JoinByKeyTest {

    private fun keyed(name: String, keyType: FieldType = FieldType.TEXT): FieldDefinition {
        return FieldDefinition(
            id = FieldId(value = name),
            type = FieldType.COLLECTION,
            fields = mapOf(
                FieldId(value = "period") to FieldDefinition(id = FieldId(value = "period"), type = keyType),
                FieldId(value = "amount") to FieldDefinition(id = FieldId(value = "amount"), type = FieldType.DECIMAL)
            )
        )
    }

    private val schema = FieldSchema(
        name = "period-balance",
        fields = listOf(
            FieldDefinition(id = FieldId(value = "periods"), type = FieldType.STRING_SET),
            keyed(name = "credits"),
            keyed(name = "debits"),
            keyed(name = "fees", keyType = FieldType.INTEGER),
        ).associateBy { definition -> definition.id }
    )

    private val nestedSchema = FieldSchema(
        name = "monthly-balance",
        fields = listOf("income", "spending").associate { name ->
            FieldId(value = name) to FieldDefinition(
                id = FieldId(value = name),
                type = FieldType.OBJECT,
                fields = mapOf(FieldId(value = "monthly") to monthly()),
            )
        }
    )

    private fun monthly(): FieldDefinition {
        return FieldDefinition(
            id = FieldId(value = "monthly"),
            type = FieldType.COLLECTION,
            fields = mapOf(
                FieldId(value = "month") to FieldDefinition(id = FieldId(value = "month"), type = FieldType.TEXT),
                FieldId(value = "amount") to FieldDefinition(id = FieldId(value = "amount"), type = FieldType.DECIMAL)
            )
        )
    }

    private val join = """joinByKey("period", credit: credits.amount, debit: debits.amount)"""

    private val periodBalance = "count(periods) > 0 and " +
        "count($join[period in periods and credit > debit]) >= count(periods) * 0.5"

    private fun rows(vararg pairs: Pair<String, Int>): List<Map<String, Any?>> {
        return pairs.map { (period, amount) -> mapOf("period" to period, "amount" to amount) }
    }

    // ── E1–E12: the period-balance check ──────────────────────────────────────

    /** E1 (A1, A8): P4 is joined but not in `periods`, so it is filtered out. */
    @Test
    fun `two of three periods positive passes and an extra period is ignored`() {
        assertTrue(actual = evaluate(condition = periodBalance, fields = e1()))
        assertTrue(
            actual = evaluate(condition = "count($join) == 4", fields = e1()),
            message = "the join itself keeps P4, with debit 0"
        )
    }

    /** E2 (A2): the threshold is `>=`. */
    @Test
    fun `exactly half passes`() {
        val fields = arrayOf(
            "periods" to listOf("P1", "P2", "P3", "P4"),
            "credits" to rows("P1" to 100, "P2" to 100, "P3" to 100, "P4" to 100),
            "debits" to rows("P1" to 50, "P2" to 50, "P3" to 150, "P4" to 150),
        )

        assertTrue(actual = evaluate(condition = periodBalance, fields = fields))
    }

    /** E3 (A3). */
    @Test
    fun `one of three positive fails`() {
        val fields = arrayOf(
            "periods" to listOf("P1", "P2", "P3"),
            "credits" to rows("P1" to 100, "P2" to 100, "P3" to 100, "P4" to 999),
            "debits" to rows("P1" to 120, "P2" to 150, "P3" to 50),
        )

        assertFalse(actual = evaluate(condition = periodBalance, fields = fields))
    }

    /** E4 (A4): `>` is strict. */
    @Test
    fun `equal amounts are not positive`() {
        val fields = arrayOf(
            "periods" to listOf("P1", "P2"),
            "credits" to rows("P1" to 100, "P2" to 100),
            "debits" to rows("P1" to 100, "P2" to 100),
        )

        assertFalse(actual = evaluate(condition = periodBalance, fields = fields))
    }

    /**
     * E5 (A5): a zero credit is simply not positive. In the ratio form the division by zero makes
     * that row's comparison undecided, so the row is not selected — and nothing is thrown.
     */
    @Test
    fun `a zero credit is not positive, with or without a division`() {
        val fields = arrayOf(
            "periods" to listOf("P1", "P2"),
            "credits" to rows("P1" to 0, "P2" to 100),
            "debits" to rows("P1" to 10, "P2" to 10),
        )
        val ratio = "count($join[period in periods and debit / credit < 1]) >= count(periods) * 0.5"

        assertTrue(actual = evaluate(condition = periodBalance, fields = fields))
        assertTrue(actual = evaluate(condition = ratio, fields = fields))
        assertTrue(
            actual = evaluate(condition = "count($join[debit / credit < 1]) == 1", fields = fields),
            message = "only P2 passes the ratio form"
        )
    }

    /** E6 (A6): the denominator is `count(periods)`, not the number of rows. */
    @Test
    fun `a period without a credit row has credit 0 and still counts`() {
        val fields = arrayOf(
            "periods" to listOf("P1", "P2", "P3"),
            "credits" to rows("P1" to 100),
            "debits" to rows("P1" to 10, "P2" to 10, "P3" to 10),
        )

        assertFalse(actual = evaluate(condition = periodBalance, fields = fields))
        assertTrue(actual = evaluate(condition = "count($join[credit == 0]) == 2", fields = fields))
    }

    /** E7 (A7, A13): outer join — a key in only one source gets 0 from the other. */
    @Test
    fun `a period without a debit row has debit 0`() {
        val fields = arrayOf(
            "periods" to listOf("P1", "P2"),
            "credits" to rows("P1" to 100, "P2" to 100),
            "debits" to rows("P1" to 200),
        )

        assertTrue(actual = evaluate(condition = periodBalance, fields = fields))
        assertTrue(actual = evaluate(condition = "count($join[debit == 0]) == 1", fields = fields))
    }

    /** E8 (A9). */
    @Test
    fun `empty periods fails`() {
        val fields = e1().map { (name, value) -> if (name == "periods") name to emptyList<String>() else name to value }

        assertFalse(actual = evaluate(condition = periodBalance, fields = fields.toTypedArray()))
    }

    /** E9 (A10): a missing source makes the join missing — undecided, never PASS. */
    @Test
    fun `a missing source makes the join missing`() {
        val fields = e1().filterNot { (name, _) -> name == "debits" }.toTypedArray()

        assertFalse(actual = evaluate(condition = periodBalance, fields = fields))
        assertFalse(
            actual = evaluate(condition = "count($join) >= 0", fields = fields),
            message = "the join has no value at all, so even a comparison every count satisfies is undecided"
        )
        assertTrue(actual = evaluate(condition = "isAvailable($join) == false", fields = fields))
        assertTrue(actual = evaluate(condition = "isAvailable($join) == true", fields = e1()))
    }

    /** E10 (A11): an empty source is valid data and contributes 0 everywhere. */
    @Test
    fun `an empty source is zero in every row`() {
        val fields = e1().map { (name, value) -> if (name == "debits") name to emptyList<Any>() else name to value }

        assertTrue(actual = evaluate(condition = periodBalance, fields = fields.toTypedArray()))
        assertTrue(actual = evaluate(condition = "count($join[debit == 0]) == 4", fields = fields.toTypedArray()))
    }

    /** E11 (A12): duplicate rows of one key are summed, as `sumByKey` does. */
    @Test
    fun `duplicate rows are summed`() {
        val fields = arrayOf(
            "periods" to listOf("P1"),
            "credits" to rows("P1" to 60, "P1" to 60),
            "debits" to rows("P1" to 100),
        )

        assertTrue(actual = evaluate(condition = periodBalance, fields = fields))
        assertTrue(actual = evaluate(condition = "count($join) == 1 and sum($join.credit) == 120", fields = fields))
    }

    /** The requirement's shape: each source is a collection nested in an object. */
    @Test
    fun `an absent nested source on a present object makes the join missing`() {
        val nestedJoin = "joinByKey(\"month\", credit: income.monthly.amount, debit: spending.monthly.amount)"
        val income = mapOf("monthly" to listOf(mapOf("month" to "M1", "amount" to 100)))

        assertFalse(
            actual = evaluate(
                condition = "count($nestedJoin) >= 0",
                fields = arrayOf("income" to income, "spending" to emptyMap<String, Any?>()),
                fieldSchema = nestedSchema,
            )
        )
        assertTrue(
            actual = evaluate(
                condition = "count($nestedJoin[debit == 0]) == 1",
                fields = arrayOf("income" to income, "spending" to mapOf("monthly" to emptyList<Any>())),
                fieldSchema = nestedSchema,
            ),
            message = "an empty nested source is data: debit is 0 in every row"
        )
    }

    // ── composition with the rest of the path language ────────────────────────

    @Test
    fun `every over the rows`() {
        assertTrue(actual = evaluate(condition = "every($join[credit > 0])", fields = e1()))
        assertFalse(actual = evaluate(condition = "every($join[credit > debit])", fields = e1()))
    }

    @Test
    fun `any over the rows`() {
        assertTrue(actual = evaluate(condition = "any($join[debit > credit])", fields = e1()))
        assertFalse(actual = evaluate(condition = "any($join[debit > 500])", fields = e1()))
    }

    /** E1's credits in first-seen order are P1–P3 at 100 and P4 at 999. */
    @Test
    fun `take keeps the first rows in first-seen key order`() {
        assertTrue(actual = evaluate(condition = "sum(take($join, 3).credit) == 300", fields = e1()))
        assertFalse(actual = evaluate(condition = "sum(take($join, 3).credit) == 1299", fields = e1()))
    }

    @Test
    fun `sortBy orders the rows by an alias`() {
        assertTrue(
            actual = evaluate(condition = """sum(take(sortBy($join, "debit", desc), 2).debit) == 200""", fields = e1())
        )
        assertFalse(
            actual = evaluate(condition = """sum(take(sortBy($join, "debit", asc), 2).debit) == 200""", fields = e1())
        )
    }

    // ── E13 (A15) and the alias rules: rejected at load time ──────────────────

    @Test
    fun `an unknown source member is rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", credit: credits.amount, debit: debits.missingMember)) > 0""",
            message = "Unknown field 'missingMember' in 'debits.missingMember'"
        )
    }

    @Test
    fun `a non-numeric value member is rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", credit: credits.period, debit: debits.amount)) > 0""",
            message = "joinByKey() sums 'period', which is declared text rather than a number"
        )
    }

    @Test
    fun `mismatched key types are rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", credit: credits.amount, fee: fees.amount)) > 0""",
            message = "joinByKey() joins on 'period', but the sources declare it as text and integer"
        )
    }

    @Test
    fun `a single source is rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", credit: credits.amount)) > 0""",
            message = "joinByKey() expects at least two sources, but got 1"
        )
    }

    @Test
    fun `a missing key literal is rejected`() {
        assertSingleError(
            condition = "count(joinByKey(credit: credits.amount, debit: debits.amount)) > 0",
            message = "joinByKey() expects the key member name as its first argument, such as " +
                "joinByKey(\"month\", income: income.amount, spending: spending.amount)"
        )
    }

    @Test
    fun `a duplicate alias is rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", credit: credits.amount, credit: debits.amount)) > 0""",
            message = "joinByKey() names the alias 'credit' twice; each source needs its own"
        )
    }

    /** Without the check the second source silently replaced the first when the aliases were mapped. */
    @Test
    fun `the compiler rejects a duplicate alias rather than dropping a source`() {
        val condition = """count(joinByKey("period", credit: credits.amount, credit: debits.amount)) > 0"""

        val failure = assertFailsWith<CompilationException> {
            Compiler.compileRules(asts = Parser(input = rule(condition = condition)).parseRules(), schema = schema)
        }

        assertEquals(expected = "join-by-key", actual = failure.ruleId)
        assertTrue(
            actual = failure.message?.contains("joinByKey() names the alias 'credit' twice") == true,
            message = "Expected the duplicate alias to be named, got: ${failure.message}"
        )
    }

    @Test
    fun `an alias named like the key is rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", period: credits.amount, debit: debits.amount)) > 0""",
            message = "joinByKey() alias 'period' is the key's name, which every row already holds"
        )
    }

    /** A filter on the rows would read the alias instead of the field, so the field becomes unreachable. */
    @Test
    fun `an alias named like a schema field is rejected`() {
        assertSingleError(
            condition = """count(joinByKey("period", periods: credits.amount, debit: debits.amount)) > 0""",
            message = "joinByKey() alias 'periods' is also a field of the schema, which a filter on the " +
                "rows could then no longer read; choose another alias"
        )
    }

    /** The rows declare the key and the aliases, so a filter naming anything else is caught. */
    @Test
    fun `a filter on the rows is checked against the row members`() {
        assertSingleError(
            condition = "count($join[credit > bogus]) > 0",
            message = "Unknown field 'bogus' in expression"
        )
    }

    @Test
    fun `the period-balance rule validates without errors`() {
        val errors = validate(condition = periodBalance).filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = emptyList(), actual = errors)
    }

    // ── harness ───────────────────────────────────────────────────────────────

    private fun e1(): Array<Pair<String, Any?>> {
        return arrayOf(
            "periods" to listOf("P1", "P2", "P3"),
            "credits" to rows("P1" to 100, "P2" to 100, "P3" to 100, "P4" to 999),
            "debits" to rows("P1" to 120, "P2" to 80, "P3" to 50),
        )
    }

    private fun assertSingleError(condition: String, message: String) {
        val errors = validate(condition = condition).filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertEquals(expected = listOf(message), actual = errors.map { diagnostic -> diagnostic.message })
        assertEquals(expected = Severity.ERROR, actual = errors.single().severity)
    }

    private fun evaluate(
        condition: String,
        fields: Array<out Pair<String, Any?>>,
        fieldSchema: FieldSchema = schema,
    ): Boolean {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = fieldSchema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = fieldSchema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun validate(condition: String): List<ValidationDiagnostic> {
        return Validator.validate(asts = Parser(input = rule(condition = condition)).parseRules(), schema = schema)
            .diagnostics
    }

    private fun rule(condition: String): String {
        return """
            rule "join-by-key" {
              description "joins credits and debits per period"
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
    }
}
