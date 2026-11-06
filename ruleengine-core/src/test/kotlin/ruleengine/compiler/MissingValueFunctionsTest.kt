package ruleengine.compiler

import ruleengine.core.domain.dto.ConditionVerdict
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
import kotlin.test.assertTrue

/**
 * `orEmpty`, `default`, `isMissing` / `isPresent` and the `optional` source marker (1.15.0), checked
 * one by one. The whole checks E and F live in `MissingValuesIntegrationTest`.
 */
class MissingValueFunctionsTest {

    private fun field(name: String, type: FieldType, members: List<FieldDefinition> = emptyList()): FieldDefinition {
        return FieldDefinition(id = FieldId(value = name), type = type, fields = members.associateBy { it.id })
    }

    private fun keyedRows(name: String): FieldDefinition {
        return field(
            name = name,
            type = FieldType.COLLECTION,
            members = listOf(
                field(name = "period", type = FieldType.TEXT),
                field(name = "amount", type = FieldType.DECIMAL),
            )
        )
    }

    private val schema = FieldSchema(
        name = "missing-values",
        fields = listOf(
            field(name = "periods", type = FieldType.STRING_SET),
            field(name = "amount", type = FieldType.DECIMAL),
            field(name = "label", type = FieldType.TEXT),
            field(
                name = "account",
                type = FieldType.OBJECT,
                members = listOf(
                    field(name = "limit", type = FieldType.DECIMAL),
                    field(
                        name = "profile",
                        type = FieldType.OBJECT,
                        members = listOf(field(name = "tags", type = FieldType.STRING_SET)),
                    ),
                ),
            ),
            field(name = "active", type = FieldType.BOOLEAN),
            field(name = "fees", type = FieldType.OBJECT, members = listOf(keyedRows(name = "rows"))),
            keyedRows(name = "credits"),
            keyedRows(name = "debits"),
            // Fields named like the new functions and the marker (C4).
            field(name = "default", type = FieldType.DECIMAL),
            field(name = "orEmpty", type = FieldType.TEXT),
            field(name = "isMissing", type = FieldType.BOOLEAN),
            field(name = "isPresent", type = FieldType.DECIMAL),
            keyedRows(name = "optional"),
        ).associateBy { it.id }
    )

    private val noRows = arrayOf<Pair<String, Any?>>("fees" to emptyMap<String, Any?>())
    private val noFees = emptyArray<Pair<String, Any?>>()

    private fun feeRows(vararg amounts: Any?): Array<Pair<String, Any?>> {
        return arrayOf("fees" to mapOf("rows" to amounts.map { amount -> mapOf("period" to "P1", "amount" to amount) }))
    }

    // ── orEmpty (R1, C1) ──────────────────────────────────────────────────────

    /** T15: the empty-collection identities, whether the list or the whole object is missing. */
    @Test
    fun `aggregates and predicates over orEmpty of a missing collection give the empty answers`() {
        val expectations = mapOf(
            "count(orEmpty(fees.rows)) == 0" to ConditionVerdict.TRUE,
            "sum(orEmpty(fees.rows).amount) == 0" to ConditionVerdict.TRUE,
            "min(orEmpty(fees.rows).amount) == 0" to ConditionVerdict.UNKNOWN,
            "max(orEmpty(fees.rows).amount) == 0" to ConditionVerdict.UNKNOWN,
            "every(orEmpty(fees.rows)[amount > 0])" to ConditionVerdict.TRUE,
            "any(orEmpty(fees.rows)[amount > 0])" to ConditionVerdict.FALSE,
        )
        listOf(noRows, noFees).forEach { record ->
            expectations.forEach { (condition, expected) ->
                assertEquals(
                    expected = expected,
                    actual = verdict(condition = condition, fields = record),
                    message = condition,
                )
            }
        }
    }

    /** T15 strict column: the same functions without orEmpty stay undecided. */
    @Test
    fun `the same aggregates without orEmpty stay undecided`() {
        val strict = listOf("count(fees.rows) == 0", "sum(fees.rows.amount) == 0", "every(fees.rows[amount > 0])")
        strict.forEach { condition ->
            assertEquals(expected = ConditionVerdict.UNKNOWN, actual = verdict(condition = condition, fields = noRows))
        }
    }

    /** T12, T13: empty is not "available", but it is empty. */
    @Test
    fun `isAvailable and isEmpty keep their meaning over orEmpty`() {
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(condition = "isAvailable(orEmpty(fees.rows))", fields = noRows),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "isEmpty(orEmpty(fees.rows))", fields = noRows),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(condition = "isEmpty(fees.rows)", fields = noRows),
        )
    }

    /** T16: a filter after orEmpty selects from the empty collection. */
    @Test
    fun `a filter after orEmpty selects nothing from a missing collection`() {
        val condition = "sum(orEmpty(fees.rows)[amount < 0].amount) == 0"

        assertEquals(expected = ConditionVerdict.TRUE, actual = verdict(condition = condition, fields = noRows))
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "sum(fees.rows[amount < 0].amount) == 0", fields = noRows)
        )
    }

    /** C1: a carried collection reads exactly as without orEmpty, including a projection and one element. */
    @Test
    fun `orEmpty of a carried collection reads the same values`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "sum(orEmpty(fees.rows.amount)) == -7", fields = feeRows(-5, -2)),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count(orEmpty(fees.rows)) == 1", fields = feeRows(-5)),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count(orEmpty(periods)) == 1", fields = arrayOf("periods" to listOf("P1")))
        )
    }

    /** T17: a missing string_set reads as an empty set, and `in` selects nothing from it. */
    @Test
    fun `orEmpty of a missing string set is an empty set`() {
        val credits = arrayOf<Pair<String, Any?>>("credits" to listOf(mapOf("period" to "P1", "amount" to 3)))

        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count(orEmpty(periods)) == 0", fields = credits),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count(credits[period in orEmpty(periods)]) == 0", fields = credits)
        )
    }

    /** T19 and C1: orEmpty only wraps something collection-valued. */
    @Test
    fun `orEmpty on a single value, an object, a variable or a keyed result is a load-time error`() {
        listOf(
            "sum(orEmpty(amount)) == 0",
            "isPresent(orEmpty(account))",
            "count(orEmpty(\$total)) == 0",
            "count(orEmpty(sumByKey(\"period\", credits.amount, debits.amount))) == 0",
            "count(orEmpty(joinByKey(\"period\", credit: credits.amount, debit: debits.amount))) == 0",
        ).forEach { condition ->
            assertTrue(actual = loadErrors(condition = condition).isNotEmpty(), message = "accepted: $condition")
        }
        assertTrue(
            actual = loadErrors(condition = "sum(orEmpty(amount)) == 0").any { it.contains("decimal") },
            message = "names the type"
        )
    }

    // ── default (R2, C2) ──────────────────────────────────────────────────────

    /** T18. */
    @Test
    fun `default answers the fallback only for a missing value`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "default(amount, 0) == 0", fields = noFees),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "default(5, 0) == 5", fields = noFees),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "default(amount, 0) == 12", fields = arrayOf("amount" to 12))
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "default(amount, 0) == 0", fields = arrayOf("amount" to "abc")),
            message = "an unreadable value is missing, so it gets the fallback"
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "default(amount, account.limit) == 0", fields = noFees),
            message = "a missing fallback leaves the result missing"
        )
    }

    /** T18: a variable no earlier rule published. */
    @Test
    fun `default replaces a variable that was never set`() {
        val rules = """
            rule "publish" {
              description "publishes a total only for large amounts"
              when
                amount > 1000
              then
                set total = amount
            }
            rule "read" {
              description "reads the total with a fallback"
              when
                default(${'$'}total, 1) == 1
              then
                flag "ok"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rules).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of("amount" to 5), schema = schema)

        val matched = RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.map { it.ruleId }

        assertEquals(expected = listOf("read"), actual = matched)
    }

    /** C2: one value only, and a fallback of the same kind. */
    @Test
    fun `default over a collection or with a fallback of another kind is a load-time error`() {
        assertTrue(actual = loadErrors(condition = "default(fees.rows.amount, 0) == 0").any { it.contains("orEmpty") })
        assertTrue(actual = loadErrors(condition = "default(periods, 0) == 0").isNotEmpty())
        assertTrue(actual = loadErrors(condition = "default(amount, \"none\") == 0").any { it.contains("same kind") })
        assertEquals(
            expected = emptyList(),
            actual = loadErrors(
                condition = "default(max(sumByKey(\"period\", credits.amount, debits.amount)), 0) <= 1"
            ),
        )
    }

    // ── isMissing / isPresent (R3, C3) ────────────────────────────────────────

    /** T14: absent, null, [] and [x] — always a decided answer. */
    @Test
    fun `isMissing and isPresent tell the three states apart and never stay undecided`() {
        val records = listOf(
            noRows to true,
            arrayOf<Pair<String, Any?>>("fees" to mapOf("rows" to null)) to true,
            arrayOf<Pair<String, Any?>>("fees" to mapOf("rows" to emptyList<Any?>())) to false,
            feeRows(-5) to false,
        )
        records.forEach { (record, missing) ->
            assertEquals(
                expected = decided(value = missing),
                actual = verdict(condition = "isMissing(fees.rows)", fields = record),
            )
            assertEquals(
                expected = decided(value = !missing),
                actual = verdict(condition = "isPresent(fees.rows)", fields = record),
            )
        }
    }

    /** C3: a filter selecting nothing does not make a carried collection missing; scalars work too. */
    @Test
    fun `presence is about the record, not about what a filter selected`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "isPresent(fees.rows[amount > 0])", fields = feeRows(-5)),
        )
        assertEquals(expected = ConditionVerdict.TRUE, actual = verdict(condition = "isPresent(fees)", fields = noRows))
        assertEquals(expected = ConditionVerdict.TRUE, actual = verdict(condition = "isMissing(fees)", fields = noFees))
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "isPresent(amount)", fields = arrayOf("amount" to 3)),
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "isMissing(amount)", fields = noFees),
        )
    }

    // ── optional (R4, C6) ─────────────────────────────────────────────────────

    /** T21: the optional source reads as empty; the other source stays strict. */
    @Test
    fun `an optional joinByKey source may be missing`() {
        val join = "joinByKey(\"period\", credit: credits.amount, optional debit: debits.amount)"
        val credits = arrayOf<Pair<String, Any?>>("credits" to listOf(mapOf("period" to "P1", "amount" to 3)))
        val debits = arrayOf<Pair<String, Any?>>("debits" to listOf(mapOf("period" to "P1", "amount" to 3)))

        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "count($join[credit > debit]) == 1", fields = credits),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = "count($join) == 1", fields = debits),
        )
    }

    @Test
    fun `every sumByKey source may be optional`() {
        val condition = "count(sumByKey(\"period\", optional credits.amount, optional debits.amount)) == 0"

        assertEquals(expected = ConditionVerdict.TRUE, actual = verdict(condition = condition, fields = noFees))
    }

    @Test
    fun `optional outside a keyed source is a load-time error`() {
        assertTrue(actual = loadErrors(condition = "count(optional credits) == 0").isNotEmpty())
        assertTrue(actual = setErrors(expression = "count(optional credits)").any { it.contains("sumByKey") })
    }

    /** `orEmpty(src.member)` as a source reads like `optional src.member`. */
    @Test
    fun `orEmpty around a whole keyed source is accepted`() {
        val condition = "count(sumByKey(\"period\", credits.amount, orEmpty(debits.amount))) == 1"
        val credits = arrayOf<Pair<String, Any?>>("credits" to listOf(mapOf("period" to "P1", "amount" to 3)))

        assertEquals(expected = emptyList(), actual = loadErrors(condition = condition))
        assertEquals(expected = ConditionVerdict.TRUE, actual = verdict(condition = condition, fields = credits))
    }

    // ── missingCollections (R6, C8) ───────────────────────────────────────────

    /** Any step missing inside a present root reads as empty; an absent root stays missing. */
    @Test
    fun `missingCollections empty reads a path inside a present root as empty`() {
        val condition = "count(account.profile.tags) == 0"
        val accountOnly = arrayOf<Pair<String, Any?>>("account" to mapOf("limit" to 5))

        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = condition, fields = accountOnly, missingCollectionsAsEmpty = true),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict(condition = condition, fields = noFees, missingCollectionsAsEmpty = true),
        )
        assertEquals(expected = ConditionVerdict.UNKNOWN, actual = verdict(condition = condition, fields = accountOnly))
    }

    // ── kinds (C2) ────────────────────────────────────────────────────────────

    /** `default` has the kind of its value, so a boolean one may stand alone as a condition. */
    @Test
    fun `a boolean default stands alone as a condition`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(condition = "default(active, true)", fields = noFees),
        )
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict(condition = "default(active, true)", fields = arrayOf("active" to false)),
        )
    }

    // ── names (C4) ────────────────────────────────────────────────────────────

    @Test
    fun `fields named like the new functions and the marker still work`() {
        val record = arrayOf<Pair<String, Any?>>(
            "default" to 7,
            "orEmpty" to "x",
            "isMissing" to true,
            "isPresent" to 2,
            "optional" to listOf(mapOf("period" to "P1", "amount" to 3)),
            "credits" to emptyList<Any?>(),
        )
        listOf(
            "default > 5",
            "orEmpty == \"x\"",
            "isMissing == true",
            "isPresent > 1",
            "count(sumByKey(\"period\", optional.amount, credits.amount)) == 1",
        ).forEach { condition ->
            assertEquals(
                expected = ConditionVerdict.TRUE,
                actual = verdict(condition = condition, fields = record),
                message = condition,
            )
        }
    }

    @Test
    fun `the new names are case-insensitive`() {
        listOf("COUNT(OrEmpty(fees.rows)) == 0", "ISMISSING(amount)", "Default(amount, 1) == 1").forEach { condition ->
            assertEquals(
                expected = ConditionVerdict.TRUE,
                actual = verdict(condition = condition, fields = noRows),
                message = condition,
            )
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun decided(value: Boolean): ConditionVerdict {
        return if (value) ConditionVerdict.TRUE else ConditionVerdict.FALSE
    }

    private fun verdict(
        condition: String,
        fields: Array<out Pair<String, Any?>>,
        missingCollectionsAsEmpty: Boolean = false,
    ): ConditionVerdict {
        val asts = Parser(input = rule(condition = condition)).parseRules()
        val compiled = Compiler.compileRules(asts = asts, schema = schema)
        val prepared = PreparedRuleContext.prepare(
            ctx = RuleContext.of(*fields),
            schema = schema,
            missingCollectionsAsEmpty = missingCollectionsAsEmpty,
        )
        return compiled.single().expression.evaluate(context = prepared, trace = null)
    }

    /** Errors the load would report: a parse error or the validator's errors. */
    private fun loadErrors(condition: String): List<String> {
        return errorsOf(text = rule(condition = condition))
    }

    /**
     * The same through a `set` clause, which has no legacy fallback: a parse error in a `when` block is
     * reported by the legacy condition parser instead.
     */
    private fun setErrors(expression: String): List<String> {
        return errorsOf(text = rule(condition = "amount > 0", then = "set result = $expression"))
    }

    private fun errorsOf(text: String): List<String> {
        return runCatching {
            Validator.validate(asts = Parser(input = text).parseRules(), schema = schema)
                .diagnostics.filter { it.severity == Severity.ERROR }.map { it.message }
        }.getOrElse { error -> listOf(error.message.orEmpty()) }
    }

    private fun rule(condition: String, then: String = "flag \"ok\""): String {
        return """
            rule "missing-values" {
              description "missing values read on request"
              when
                $condition
              then
                $then
              not_exists
                flag "undecided"
            }
        """.trimIndent()
    }
}
