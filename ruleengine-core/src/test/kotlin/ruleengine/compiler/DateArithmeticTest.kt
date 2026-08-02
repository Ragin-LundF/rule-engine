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

/** `daysBetween(from, to)` — REQ-04. */
class DateArithmeticTest {

    private val schema = FieldSchema(
        name = "date-schema",
        fields = mapOf(
            FieldId(value = "registeredAt") to FieldDefinition(
                id = FieldId(value = "registeredAt"),
                type = FieldType.DATE
            ),
            FieldId(value = "submittedAt") to FieldDefinition(
                id = FieldId(value = "submittedAt"),
                type = FieldType.DATE
            ),
            FieldId(value = "lastSeenAt") to FieldDefinition(
                id = FieldId(value = "lastSeenAt"),
                type = FieldType.DATE_TIME
            ),
            FieldId(value = "germanDate") to FieldDefinition(
                id = FieldId(value = "germanDate"),
                type = FieldType.DATE,
                format = "dd.MM.yyyy"
            ),
            FieldId(value = "name") to FieldDefinition(
                id = FieldId(value = "name"),
                type = FieldType.TEXT
            )
        )
    )

    @Test
    fun `days between two dates matches when the span is long enough`() {
        assertTrue(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) >= 90",
                "registeredAt" to "2024-01-01",
                "submittedAt" to "2024-04-01"
            ),
            message = "1 January to 1 April is 91 days"
        )
    }

    @Test
    fun `days between two dates does not match when the span is too short`() {
        assertFalse(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) >= 90",
                "registeredAt" to "2024-01-01",
                "submittedAt" to "2024-02-01"
            ),
            message = "31 days is short of the 90-day threshold"
        )
    }

    /** Second operand minus first, so an earlier second operand is negative. */
    @Test
    fun `the result is signed`() {
        assertTrue(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) < 0",
                "registeredAt" to "2024-04-01",
                "submittedAt" to "2024-01-01"
            ),
            message = "a second operand that comes first must produce a negative span"
        )
    }

    @Test
    fun `identical dates are zero days apart`() {
        assertTrue(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) == 0",
                "registeredAt" to "2024-04-01",
                "submittedAt" to "2024-04-01"
            )
        )
    }

    /** Date-time values are compared at calendar-day precision. */
    @Test
    fun `a date-time operand is truncated to its calendar day`() {
        assertTrue(
            actual = evaluate(
                condition = "daysBetween(registeredAt, lastSeenAt) == 1",
                "registeredAt" to "2024-04-01",
                "lastSeenAt" to "2024-04-02T23:59:59"
            ),
            message = "the time of day must not add a day"
        )
    }

    /** The declared `format` is only applied when preparing the field, so this proves it is read. */
    @Test
    fun `a field with a declared format is read with it`() {
        assertTrue(
            actual = evaluate(
                condition = "daysBetween(germanDate, submittedAt) == 31",
                "germanDate" to "01.01.2024",
                "submittedAt" to "2024-02-01"
            )
        )
    }

    /** A missing operand produces a missing result, and a comparison against missing is false. */
    @Test
    fun `a missing operand does not match and does not throw`() {
        assertFalse(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) >= 0",
                "registeredAt" to "2024-01-01"
            ),
            message = "an absent second date yields a missing result, not an exception"
        )
    }

    @Test
    fun `an unparseable operand does not match and does not throw`() {
        assertFalse(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) >= 0",
                "registeredAt" to "2024-01-01",
                "submittedAt" to "not-a-date"
            )
        )
    }

    @Test
    fun `the result is usable in arithmetic`() {
        assertTrue(
            actual = evaluate(
                condition = "daysBetween(registeredAt, submittedAt) / 30 >= 3",
                "registeredAt" to "2024-01-01",
                "submittedAt" to "2024-04-01"
            ),
            message = "91 days is three whole months"
        )
    }

    // --- validation ---

    @Test
    fun `a text operand is rejected at validation time`() {
        val diagnostics = validate(condition = "daysBetween(name, submittedAt) >= 0")
        val error = diagnostics.firstOrNull { diagnostic -> diagnostic.message.contains(other = "daysBetween") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
    }

    @Test
    fun `the wrong number of arguments is rejected at validation time`() {
        val diagnostics = validate(condition = "daysBetween(registeredAt) >= 0")
        val error = diagnostics.firstOrNull { diagnostic -> diagnostic.message.contains(other = "daysBetween") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
        assertTrue(
            actual = error?.message?.contains(other = "exactly 2 arguments") == true,
            message = "the diagnostic must say how many arguments are expected, got: ${error?.message}"
        )
    }

    @Test
    fun `two dates validate without errors`() {
        val errors = validate(condition = "daysBetween(registeredAt, submittedAt) >= 90")
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertTrue(actual = errors.isEmpty(), message = "the documented form must not error, got: $errors")
    }

    /** A date written inline is a string literal, and must be accepted where a date is expected. */
    @Test
    fun `an ISO date literal is accepted as an operand`() {
        val errors = validate(condition = """daysBetween(registeredAt, "2024-04-01") >= 90""")
            .filter { diagnostic -> diagnostic.severity == Severity.ERROR }

        assertTrue(actual = errors.isEmpty(), message = "an ISO literal must be accepted, got: $errors")
        assertTrue(
            actual = evaluate(
                condition = """daysBetween(registeredAt, "2024-04-01") >= 90""",
                "registeredAt" to "2024-01-01"
            ),
            message = "and it must evaluate as a date"
        )
    }

    @Test
    fun `a string literal that is not a date is rejected`() {
        val error = validate(condition = """daysBetween(registeredAt, "yesterday") >= 90""")
            .firstOrNull { diagnostic -> diagnostic.message.contains(other = "daysBetween") }

        assertEquals(expected = Severity.ERROR, actual = error?.severity)
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
        rule "date-test" {
          when
            $condition
          then
            flag "ok"
        }
    """.trimIndent()
}
