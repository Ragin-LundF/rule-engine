package ruleengine.compiler

import ruleengine.core.domain.dto.FieldDefinition
import ruleengine.core.domain.dto.FieldId
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.domain.dto.FieldType
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingDescriptionWarningTest {

    private val schema = FieldSchema(
        name = "test-schema",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            )
        )
    )

    private fun validate(rules: String): ValidationResult {
        val asts = Parser(input = rules).parseRules()
        return Validator.validate(asts = asts, schema = schema)
    }

    private fun descriptionWarnings(result: ValidationResult): List<String> {
        return result.diagnostics
            .filter { diagnostic -> diagnostic.message.contains("has no description") }
            .map { diagnostic -> diagnostic.message }
    }

    @Test
    fun `a rule without a description produces one warning naming the rule`() {
        val result = validate(
            rules = """
                rule "undescribed" {
                  when
                    amount >= 1
                  then
                    label "a"
                }
            """.trimIndent()
        )

        assertEquals(
            expected = listOf("Rule 'undescribed' has no description"),
            actual = descriptionWarnings(result = result)
        )
    }

    @Test
    fun `the missing description warning never invalidates the rule set`() {
        val result = validate(
            rules = """
                rule "undescribed" {
                  when
                    amount >= 1
                  then
                    label "a"
                }
            """.trimIndent()
        )

        assertTrue(
            actual = result.isValid,
            message = "A missing description must not block compilation: ${result.diagnostics}"
        )
        assertTrue(
            actual = result.diagnostics.all { diagnostic -> diagnostic.severity == Severity.WARNING },
            message = "Expected only warnings, got: ${result.diagnostics}"
        )
    }

    @Test
    fun `a described rule produces no warning`() {
        val result = validate(
            rules = """
                rule "described" {
                  description "Payments of at least one unit."
                  when
                    amount >= 1
                  then
                    label "a"
                }
            """.trimIndent()
        )

        assertEquals(expected = emptyList(), actual = descriptionWarnings(result = result))
    }

    @Test
    fun `a blank description counts as missing`() {
        val result = validate(
            rules = """
                rule "blank" {
                  description "   "
                  when
                    amount >= 1
                  then
                    label "a"
                }
            """.trimIndent()
        )

        assertEquals(
            expected = listOf("Rule 'blank' has no description"),
            actual = descriptionWarnings(result = result)
        )
    }

    @Test
    fun `each undescribed rule is reported separately`() {
        val result = validate(
            rules = """
                rule "a" {
                  when
                    amount >= 1
                  then
                    label "a"
                }

                rule "b" {
                  description "Described."
                  when
                    amount >= 2
                  then
                    label "b"
                }

                rule "c" {
                  when
                    amount >= 3
                  then
                    label "c"
                }
            """.trimIndent()
        )

        assertEquals(
            expected = listOf("Rule 'a' has no description", "Rule 'c' has no description"),
            actual = descriptionWarnings(result = result)
        )
    }

    @Test
    fun `the warning carries a suggestion pointing at the clause`() {
        val result = validate(
            rules = """
                rule "undescribed" {
                  when
                    amount >= 1
                  then
                    label "a"
                }
            """.trimIndent()
        )

        val warning = result.diagnostics.single { diagnostic ->
            diagnostic.message.contains("has no description")
        }
        assertTrue(
            actual = warning.suggestion.orEmpty().contains("description"),
            message = "Expected an actionable suggestion, got: ${warning.suggestion}"
        )
    }
}
