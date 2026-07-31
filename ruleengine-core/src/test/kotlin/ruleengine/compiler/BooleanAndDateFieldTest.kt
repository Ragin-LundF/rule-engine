package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.evaluator.RuleEngine
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.RuleContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Boolean and date fields, end to end.
 *
 * This file replaces `UnsupportedFieldTypeValidationTest`, which asserted that both types were
 * rejected — the behaviour RULE-SPEC.md documented as working while the engine refused it.
 */
class BooleanAndDateFieldTest {

    private val schema = FieldSchema(
        name = "flags-and-dates",
        fields = listOf(
            FieldDefinition(id = FieldId(value = "isActive"), type = FieldType.BOOLEAN),
            FieldDefinition(id = FieldId(value = "verified"), type = FieldType.BOOLEAN),
            FieldDefinition(id = FieldId(value = "createdAt"), type = FieldType.DATE),
            FieldDefinition(id = FieldId(value = "bookedAt"), type = FieldType.DATE_TIME),
            FieldDefinition(id = FieldId(value = "dueDate"), type = FieldType.DATE, format = "dd.MM.yyyy"),
            FieldDefinition(
                id = FieldId(value = "eventAt"),
                type = FieldType.DATE_TIME,
                format = "dd.MM.yyyy HH:mm",
            ),
        ).associateBy { it.id },
    )

    private fun validate(condition: String): ValidationResult {
        val rule = """
            rule "test" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        return Validator.validate(asts = Parser(input = rule).parseRules(), schema = schema)
    }

    private fun matches(condition: String, vararg fields: Pair<String, Any?>): Boolean {
        val rule = """
            rule "test" {
              when
                $condition
              then
                flag "ok"
            }
        """.trimIndent()
        val compiled = Compiler.compileRules(asts = Parser(input = rule).parseRules(), schema = schema)
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = compiled).evaluate(prepared = prepared).matches.isNotEmpty()
    }

    private fun assertValid(condition: String) {
        val errors = validate(condition = condition).diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(
            actual = errors.isEmpty(),
            message = "'$condition' should validate, got: ${errors.map { it.message }}",
        )
    }

    // ── boolean ───────────────────────────────────────────────────────────────

    @Test
    fun `boolean equals true validates and evaluates`() {
        assertValid(condition = "isActive equals true")
        assertTrue(actual = matches(condition = "isActive equals true", "isActive" to true))
        assertFalse(actual = matches(condition = "isActive equals true", "isActive" to false))
    }

    @Test
    fun `boolean equals false validates and evaluates`() {
        assertValid(condition = "isActive equals false")
        assertTrue(actual = matches(condition = "isActive equals false", "isActive" to false))
        assertFalse(actual = matches(condition = "isActive equals false", "isActive" to true))
    }

    @Test
    fun `boolean accepts string input`() {
        assertTrue(actual = matches(condition = "isActive equals true", "isActive" to "TRUE"))
        assertFalse(actual = matches(condition = "isActive equals true", "isActive" to "false"))
    }

    @Test
    fun `missing or non-boolean input does not match`() {
        assertFalse(actual = matches(condition = "isActive equals true"))
        assertFalse(actual = matches(condition = "isActive equals true", "isActive" to "yes"))
        assertFalse(actual = matches(condition = "isActive equals false", "isActive" to 1))
    }

    @Test
    fun `symbolic equality on a boolean field works`() {
        assertValid(condition = "isActive == true")
        assertTrue(actual = matches(condition = "isActive == true", "isActive" to true))
        assertTrue(actual = matches(condition = "isActive != true", "isActive" to false))
    }

    @Test
    fun `not on a boolean condition inverts it`() {
        assertTrue(actual = matches(condition = "not isActive equals true", "isActive" to false))
        assertFalse(actual = matches(condition = "not isActive equals true", "isActive" to true))
    }

    @Test
    fun `two boolean conditions combine`() {
        assertValid(condition = "isActive equals true and verified equals true")
        assertTrue(
            actual = matches(
                condition = "isActive equals true and verified equals true",
                "isActive" to true,
                "verified" to true,
            )
        )
        assertFalse(
            actual = matches(
                condition = "isActive equals true and verified equals true",
                "isActive" to true,
                "verified" to false,
            )
        )
    }

    @Test
    fun `non-boolean literal on a boolean field is rejected`() {
        val result = validate(condition = """isActive equals "yes"""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any { it.message.contains(other = "expects 'true' or 'false'") },
            message = "Expected a boolean literal diagnostic, got: ${result.diagnostics.map { it.message }}",
        )
    }

    @Test
    fun `ordering operators are not allowed on booleans`() {
        assertFalse(actual = validate(condition = "isActive gt true").isValid)
    }

    // ── date ──────────────────────────────────────────────────────────────────

    @Test
    fun `all date comparison operators work`() {
        val cases = listOf(
            """createdAt equals "2024-06-15"""" to true,
            """createdAt equals "2024-06-16"""" to false,
            """createdAt gt "2024-06-14"""" to true,
            """createdAt gt "2024-06-15"""" to false,
            """createdAt gte "2024-06-15"""" to true,
            """createdAt lt "2024-06-16"""" to true,
            """createdAt lt "2024-06-15"""" to false,
            """createdAt lte "2024-06-15"""" to true,
        )
        cases.forEach { (condition, expected) ->
            assertValid(condition = condition)
            val actual = matches(condition = condition, "createdAt" to LocalDate.of(2024, 6, 15))
            assertTrue(
                actual = actual == expected,
                message = "'$condition' expected $expected but was $actual",
            )
        }
    }

    @Test
    fun `symbolic date comparison works`() {
        assertValid(condition = """createdAt >= "2024-01-01"""")
        assertTrue(
            actual = matches(condition = """createdAt >= "2024-01-01"""", "createdAt" to "2024-06-15")
        )
        assertFalse(
            actual = matches(condition = """createdAt >= "2024-01-01"""", "createdAt" to "2023-12-31")
        )
    }

    @Test
    fun `date between is inclusive on both bounds`() {
        val condition = """createdAt between "2024-01-01" "2024-12-31""""
        assertValid(condition = condition)
        assertTrue(actual = matches(condition = condition, "createdAt" to "2024-01-01"))
        assertTrue(actual = matches(condition = condition, "createdAt" to "2024-12-31"))
        assertTrue(actual = matches(condition = condition, "createdAt" to "2024-06-15"))
        assertFalse(actual = matches(condition = condition, "createdAt" to "2023-12-31"))
        assertFalse(actual = matches(condition = condition, "createdAt" to "2025-01-01"))
    }

    @Test
    fun `date accepts string, LocalDate, LocalDateTime and Instant input`() {
        val condition = """createdAt equals "2024-06-15""""
        assertTrue(actual = matches(condition = condition, "createdAt" to "2024-06-15"))
        assertTrue(actual = matches(condition = condition, "createdAt" to LocalDate.of(2024, 6, 15)))
        assertTrue(
            actual = matches(condition = condition, "createdAt" to LocalDateTime.of(2024, 6, 15, 23, 59))
        )
        assertTrue(
            actual = matches(condition = condition, "createdAt" to Instant.parse("2024-06-15T12:00:00Z"))
        )
    }

    @Test
    fun `missing or unparseable date input does not match`() {
        val condition = """createdAt equals "2024-06-15""""
        assertFalse(actual = matches(condition = condition))
        assertFalse(actual = matches(condition = condition, "createdAt" to "15.06.2024"))
        assertFalse(actual = matches(condition = condition, "createdAt" to 20240615))
    }

    @Test
    fun `invalid date literal is rejected at validation time`() {
        val result = validate(condition = """createdAt equals "15.06.2024"""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any { it.message.contains(other = "YYYY-MM-DD") },
            message = "Expected an ISO date diagnostic, got: ${result.diagnostics.map { it.message }}",
        )
    }

    @Test
    fun `invalid date bound in between is rejected`() {
        assertFalse(actual = validate(condition = """createdAt between "2024-01-01" "nope"""").isValid)
    }

    @Test
    fun `text operators are not allowed on dates`() {
        assertFalse(actual = validate(condition = """createdAt contains "2024"""").isValid)
    }

    // ── date_time ─────────────────────────────────────────────────────────────

    @Test
    fun `all date_time comparison operators work`() {
        val cases = listOf(
            """bookedAt equals "2024-06-15T09:00:00"""" to true,
            """bookedAt equals "2024-06-15T09:00:01"""" to false,
            """bookedAt gt "2024-06-15T08:59:59"""" to true,
            """bookedAt gte "2024-06-15T09:00:00"""" to true,
            """bookedAt lt "2024-06-15T09:00:01"""" to true,
            """bookedAt lte "2024-06-15T09:00:00"""" to true,
        )
        cases.forEach { (condition, expected) ->
            assertValid(condition = condition)
            val actual = matches(condition = condition, "bookedAt" to "2024-06-15T09:00:00")
            assertTrue(
                actual = actual == expected,
                message = "'$condition' expected $expected but was $actual",
            )
        }
    }

    /** The behaviour that separates `date_time` from `date`: the time of day decides the outcome. */
    @Test
    fun `date_time compares the time of day, unlike date`() {
        val condition = """bookedAt gt "2024-06-15T09:00:00""""
        assertFalse(actual = matches(condition = condition, "bookedAt" to "2024-06-15T09:00:00"))
        assertTrue(actual = matches(condition = condition, "bookedAt" to "2024-06-15T09:00:01"))

        // the same instant on a `date` field is truncated, so the time cannot decide anything
        assertFalse(
            actual = matches(condition = """createdAt gt "2024-06-15"""", "createdAt" to "2024-06-15T09:00:01")
        )
    }

    @Test
    fun `date_time between is inclusive on both bounds`() {
        val condition = """bookedAt between "2024-06-15T09:00:00" "2024-06-15T17:00:00""""
        assertValid(condition = condition)
        assertTrue(actual = matches(condition = condition, "bookedAt" to "2024-06-15T09:00:00"))
        assertTrue(actual = matches(condition = condition, "bookedAt" to "2024-06-15T17:00:00"))
        assertTrue(actual = matches(condition = condition, "bookedAt" to "2024-06-15T12:30:00"))
        assertFalse(actual = matches(condition = condition, "bookedAt" to "2024-06-15T08:59:59"))
        assertFalse(actual = matches(condition = condition, "bookedAt" to "2024-06-15T17:00:01"))
    }

    @Test
    fun `date_time accepts string, LocalDate, LocalDateTime and Instant input`() {
        val condition = """bookedAt equals "2024-06-15T09:30:00""""
        assertTrue(actual = matches(condition = condition, "bookedAt" to "2024-06-15T09:30:00"))
        assertTrue(
            actual = matches(condition = condition, "bookedAt" to LocalDateTime.of(2024, 6, 15, 9, 30))
        )
        assertTrue(
            actual = matches(condition = condition, "bookedAt" to Instant.parse("2024-06-15T09:30:00Z"))
        )
        // a date-only input starts at midnight
        assertTrue(
            actual = matches(
                condition = """bookedAt equals "2024-06-15T00:00:00"""",
                "bookedAt" to LocalDate.of(2024, 6, 15),
            )
        )
    }

    @Test
    fun `text operators are not allowed on date_time`() {
        assertFalse(actual = validate(condition = """bookedAt contains "2024"""").isValid)
    }

    // ── declared format ───────────────────────────────────────────────────────

    @Test
    fun `a formatted date field reads input and literals in its own format`() {
        val condition = """dueDate equals "31.01.2024""""
        assertValid(condition = condition)
        assertTrue(actual = matches(condition = condition, "dueDate" to "31.01.2024"))
        assertFalse(actual = matches(condition = condition, "dueDate" to "01.02.2024"))
    }

    @Test
    fun `a formatted date field does not accept ISO input`() {
        assertFalse(actual = matches(condition = """dueDate equals "31.01.2024"""", "dueDate" to "2024-01-31"))
    }

    @Test
    fun `an ISO literal on a formatted field is rejected at validation time`() {
        val result = validate(condition = """dueDate equals "2024-01-31"""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any { it.message.contains(other = "format 'dd.MM.yyyy'") },
            message = "Expected a declared-format diagnostic, got: ${result.diagnostics.map { it.message }}",
        )
    }

    @Test
    fun `a typed input value is accepted on a formatted field`() {
        // an already-typed value carries no text, so the declared pattern does not apply to it
        assertTrue(
            actual = matches(condition = """dueDate equals "31.01.2024"""", "dueDate" to LocalDate.of(2024, 1, 31))
        )
    }

    @Test
    fun `unparseable input on a formatted field does not match`() {
        val condition = """dueDate equals "31.01.2024""""
        assertFalse(actual = matches(condition = condition, "dueDate" to "31/01/2024"))
        assertFalse(actual = matches(condition = condition))
    }

    @Test
    fun `a formatted date field supports ranges in its own format`() {
        val condition = """dueDate between "01.01.2024" "31.12.2024""""
        assertValid(condition = condition)
        assertTrue(actual = matches(condition = condition, "dueDate" to "31.01.2024"))
        assertFalse(actual = matches(condition = condition, "dueDate" to "01.01.2025"))
    }

    @Test
    fun `an invalid bound on a formatted field is rejected`() {
        val result = validate(condition = """dueDate between "01.01.2024" "2024-12-31"""")
        assertFalse(actual = result.isValid)
        assertTrue(
            actual = result.diagnostics.any { it.message.contains(other = "format 'dd.MM.yyyy'") },
            message = "Expected a declared-format diagnostic, got: ${result.diagnostics.map { it.message }}",
        )
    }

    @Test
    fun `a formatted date_time field compares the time of day`() {
        val condition = """eventAt gt "15.06.2024 09:00""""
        assertValid(condition = condition)
        assertTrue(actual = matches(condition = condition, "eventAt" to "15.06.2024 09:01"))
        assertFalse(actual = matches(condition = condition, "eventAt" to "15.06.2024 09:00"))
        assertFalse(actual = matches(condition = condition, "eventAt" to "2024-06-15T09:01:00"))
    }
}
