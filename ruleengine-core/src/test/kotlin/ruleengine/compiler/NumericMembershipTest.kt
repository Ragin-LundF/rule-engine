package ruleengine.compiler

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldDefinition
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.domain.dto.field.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `in` on the scalar types that are not text: `integer`, `decimal`, `date` and `date_time`.
 *
 * It used to be text-only, so `statusCode in [401, 403]` — the obvious way to write a set-membership
 * test against a number — was rejected by the validator and threw in the compiler. A bundled sample
 * schema declared it anyway, which is how the gap surfaced.
 */
class NumericMembershipTest {

    private val schema = FieldSchema(
        name = "membership-schema",
        fields = mapOf(
            FieldId(value = "statusCode") to FieldDefinition(
                id = FieldId(value = "statusCode"),
                type = FieldType.INTEGER,
            ),
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL,
            ),
            FieldId(value = "openedAt") to FieldDefinition(
                id = FieldId(value = "openedAt"),
                type = FieldType.DATE,
            ),
            FieldId(value = "bookedAt") to FieldDefinition(
                id = FieldId(value = "bookedAt"),
                type = FieldType.DATE_TIME,
            ),
            FieldId(value = "germanDate") to FieldDefinition(
                id = FieldId(value = "germanDate"),
                type = FieldType.DATE,
                format = "dd.MM.yyyy",
            ),
        )
    )

    // ── integer ───────────────────────────────────────────────────────────────

    @Test
    fun `an integer in the list matches`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("statusCode in [401, 403]", "statusCode" to 403),
        )
    }

    @Test
    fun `an integer outside the list does not match`() {
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict("statusCode in [401, 403]", "statusCode" to 200),
        )
    }

    @Test
    fun `a bare integer literal is a set of one`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("statusCode in 401", "statusCode" to 401),
        )
    }

    // ── decimal ───────────────────────────────────────────────────────────────

    @Test
    fun `a decimal in the list matches`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("amount in [1.5, 2.5]", "amount" to 2.5),
        )
    }

    /** The same rule every other numeric comparison follows: `1` and `1.0` are one number. */
    @Test
    fun `a decimal matches a value written with a different scale`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("amount in [1, 2]", "amount" to 2.0),
            message = "BigDecimal.equals compares scale; membership must compare value",
        )
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("amount in [1.00, 2.00]", "amount" to 2),
        )
    }

    // ── temporal ──────────────────────────────────────────────────────────────

    @Test
    fun `a date in the list matches`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("""openedAt in ["2024-01-01", "2024-07-01"]""", "openedAt" to "2024-07-01"),
        )
    }

    @Test
    fun `a date outside the list does not match`() {
        assertEquals(
            expected = ConditionVerdict.FALSE,
            actual = verdict("""openedAt in ["2024-01-01", "2024-07-01"]""", "openedAt" to "2024-03-01"),
        )
    }

    @Test
    fun `a date_time in the list matches on its instant`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict(
                """bookedAt in ["2024-01-01T09:30:00", "2024-01-01T17:00:00"]""",
                "bookedAt" to "2024-01-01T17:00:00",
            ),
        )
    }

    /** Every item is read under the field's declared `format`, as a single literal already is. */
    @Test
    fun `a date list is read in the field's declared format`() {
        assertEquals(
            expected = ConditionVerdict.TRUE,
            actual = verdict("""germanDate in ["01.02.2024", "03.04.2024"]""", "germanDate" to "03.04.2024"),
        )
    }

    // ── missing data propagates, as everywhere else ───────────────────────────

    @Test
    fun `a missing field leaves the membership test undecided`() {
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict("statusCode in [401, 403]", "amount" to 1),
        )
        assertEquals(
            expected = ConditionVerdict.UNKNOWN,
            actual = verdict("""openedAt in ["2024-01-01"]""", "amount" to 1),
        )
    }

    // ── a bad list is a diagnostic, not a thrown exception ────────────────────

    @Test
    fun `a text item in an integer list is reported`() {
        assertDiagnostic(condition = """statusCode in ["abc"]""")
    }

    @Test
    fun `a fractional item in an integer list is reported`() {
        assertDiagnostic(condition = "statusCode in [1.5]")
    }

    @Test
    fun `a malformed date in a list is reported`() {
        assertDiagnostic(condition = """openedAt in ["2024-01-01", "not-a-date"]""")
    }

    @Test
    fun `a date in the wrong format for the field is reported`() {
        assertDiagnostic(condition = """germanDate in ["2024-02-01"]""")
    }

    @Test
    fun `a well formed list produces no diagnostic`() {
        assertTrue(
            actual = errorsOf(condition = "statusCode in [401, 403]").isEmpty(),
            message = "the ordinary form must validate clean",
        )
    }

    private fun assertDiagnostic(condition: String) {
        val errors = errorsOf(condition = condition)
        assertTrue(
            actual = errors.isNotEmpty(),
            message = "'$condition' must be reported as a diagnostic rather than thrown at compile time",
        )
    }

    private fun errorsOf(condition: String) =
        Validator.validate(asts = Parser(input = rule(condition)).parseRules(), schema = schema)
            .diagnostics
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }

    private fun verdict(condition: String, vararg fields: Pair<String, Any?>): ConditionVerdict {
        val compiled = Compiler.compileRules(asts = Parser(input = rule(condition)).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return compiled.single().expression.evaluate(context = prepared, trace = null)
    }

    private fun rule(condition: String) = """
        rule "membership" {
          description "membership over a non-text scalar"
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
