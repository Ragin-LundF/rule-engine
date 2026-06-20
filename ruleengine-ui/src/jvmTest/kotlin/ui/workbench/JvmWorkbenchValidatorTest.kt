package ui.workbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SCHEMA_TEXT = """
schema: transaction-v1
fields:
  purpose:
    type: text
    operators: [contains, equals]
  amount:
    type: decimal
    operators: [gte, lte, gt, lt, equals]
""".trimIndent()

private val ACTIONS_TEXT = """
actions:
  label:
    argTypes: [string]
""".trimIndent()

private val VALID_RULE = """
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 500
  then
    label "rent"
}
""".trimIndent()

class JvmWorkbenchValidatorTest {

    private val validator = JvmWorkbenchValidator()

    @Test
    fun `valid rule with schema and actions produces VALID state and no error diagnostics`() {
        val result = validator.validate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = VALID_RULE,
        )

        assertEquals(expected = ValidationState.VALID, actual = result.validationState)
        assertTrue(
            actual = result.diagnostics.none { it.severity == UiDiagnosticSeverity.ERROR },
            message = "Expected no ERROR diagnostics but got: ${result.diagnostics}",
        )
    }

    @Test
    fun `unknown field produces INVALID state with error diagnostic`() {
        val ruleWithUnknownField = """
            rule "bad-field" {
              when
                nonexistent_field equals "x"
              then
                label "test"
            }
        """.trimIndent()

        val result = validator.validate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = ruleWithUnknownField,
        )

        assertEquals(expected = ValidationState.INVALID, actual = result.validationState)
        assertTrue(
            actual = result.diagnostics.any { it.severity == UiDiagnosticSeverity.ERROR },
            message = "Expected at least one ERROR diagnostic for unknown field",
        )
    }

    @Test
    fun `wrong operator for field type produces INVALID state with error diagnostic`() {
        // 'contains' is a text operator; 'amount' is decimal — should fail
        val ruleWithWrongOperator = """
            rule "wrong-op" {
              when
                amount contains "rent"
              then
                label "test"
            }
        """.trimIndent()

        val result = validator.validate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = ruleWithWrongOperator,
        )

        assertEquals(expected = ValidationState.INVALID, actual = result.validationState)
        assertTrue(
            actual = result.diagnostics.any { it.severity == UiDiagnosticSeverity.ERROR },
            message = "Expected at least one ERROR diagnostic for wrong operator",
        )
    }

    @Test
    fun `unknown action with action schema present produces INVALID state`() {
        val ruleWithUnknownAction = """
            rule "bad-action" {
              when
                purpose contains "rent"
              then
                unknownAction "value"
            }
        """.trimIndent()

        val result = validator.validate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = ruleWithUnknownAction,
        )

        assertEquals(expected = ValidationState.INVALID, actual = result.validationState)
        assertTrue(
            actual = result.diagnostics.any { it.severity == UiDiagnosticSeverity.ERROR },
            message = "Expected at least one ERROR diagnostic for unknown action",
        )
    }

    @Test
    fun `blank schema and blank rule text produces IDLE state`() {
        val result = validator.validate(
            schemaText = "",
            actionsText = "",
            ruleText = "",
        )

        assertEquals(expected = ValidationState.IDLE, actual = result.validationState)
    }

    @Test
    fun `valid rule without action schema still validates successfully`() {
        val result = validator.validate(
            schemaText = SCHEMA_TEXT,
            actionsText = "",
            ruleText = VALID_RULE,
        )

        assertEquals(expected = ValidationState.VALID, actual = result.validationState)
    }
}
