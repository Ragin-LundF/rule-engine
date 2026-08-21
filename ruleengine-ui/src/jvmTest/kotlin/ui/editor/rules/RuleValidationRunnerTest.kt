package ui.editor.rules

import ruleengine.core.errors.Severity
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.editor.rules.model.RuleValidationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
  description "A recurring payment whose purpose mentions rent."
  when
    purpose contains "rent"
    and amount >= 500
  then
    label "rent"
}
""".trimIndent()

/**
 * The single parse-and-validate path both editors now use.
 *
 * Fixtures carried over verbatim from `JvmWorkbenchValidatorTest` so the scenarios that class covers
 * keep coverage once it goes: a clean rule, an unknown field, a wrong operator, an unknown action,
 * and text the parser cannot read at all.
 */
class RuleValidationRunnerTest {

    private val schema = FieldSchemaLoader.loadFromString(content = SCHEMA_TEXT, nameHint = "transaction-v1")
    private val actions = ActionSchemaLoader.loadFromString(content = ACTIONS_TEXT)

    private fun run(rule: String) = RuleValidationRunner.run(ruleText = rule, schema = schema, actions = actions)

    @Test
    fun `a correct rule validates with no error diagnostics`() {
        val outcome = assertIs<RuleValidationOutcome.Completed>(value = run(rule = VALID_RULE))

        assertTrue(actual = outcome.isValid, message = "unexpected: ${outcome.diagnostics}")
        assertTrue(actual = outcome.diagnostics.none { it.severity == Severity.ERROR })
    }

    @Test
    fun `an unknown field is an error, not a throw`() {
        val rule = """
            rule "bad-field" {
              description "d"
              when
                nonexistent_field equals "x"
              then
                label "y"
            }
        """.trimIndent()
        val outcome = assertIs<RuleValidationOutcome.Completed>(value = run(rule = rule))

        assertEquals(expected = false, actual = outcome.isValid)
        assertTrue(
            actual = outcome.diagnostics.any { it.severity == Severity.ERROR && "nonexistent_field" in it.message },
            message = "got: ${outcome.diagnostics}",
        )
    }

    @Test
    fun `an operator the field does not allow is an error`() {
        val rule = """
            rule "bad-operator" {
              description "d"
              when
                amount contains "x"
              then
                label "y"
            }
        """.trimIndent()
        val outcome = assertIs<RuleValidationOutcome.Completed>(value = run(rule = rule))

        assertEquals(expected = false, actual = outcome.isValid)
        assertTrue(actual = outcome.diagnostics.any { it.severity == Severity.ERROR })
    }

    @Test
    fun `an unknown action is an error`() {
        val rule = """
            rule "bad-action" {
              description "d"
              when
                purpose contains "rent"
              then
                nosuchaction "y"
            }
        """.trimIndent()
        val outcome = assertIs<RuleValidationOutcome.Completed>(value = run(rule = rule))

        assertEquals(expected = false, actual = outcome.isValid)
        assertTrue(
            actual = outcome.diagnostics.any { "nosuchaction" in it.message },
            message = "got: ${outcome.diagnostics}",
        )
    }

    /**
     * Text the parser cannot read comes back as [RuleValidationOutcome.Threw] rather than escaping —
     * which is what lets the debounced pass ignore it while someone is mid-keystroke.
     */
    @Test
    fun `unparseable text is returned as a failure, not thrown`() {
        assertIs<RuleValidationOutcome.Threw>(value = run(rule = "this is not a rule at all {{{"))
    }

    /**
     * The bug this parameter exists for: a manifest's last rule file reads what the files before it
     * accumulated, and validating it on its own used to report every one of those reads as unknown.
     */
    @Test
    fun `a read of a variable an earlier file publishes is accepted`() {
        val rule = """
            rule "assessment" {
              description "Reads a list an earlier file of the entry fills."
              when
                not ${'$'}failedChecks contains "negative"
              then
                label "ok"
            }
        """.trimIndent()

        val withoutScope = assertIs<RuleValidationOutcome.Completed>(value = run(rule = rule))
        assertTrue(
            actual = withoutScope.diagnostics.any { "unknown variable" in it.message },
            message = "expected the unknown-variable error without an inherited scope",
        )

        val withScope = assertIs<RuleValidationOutcome.Completed>(
            value = RuleValidationRunner.run(
                ruleText = rule,
                schema = schema,
                actions = actions,
                inheritedVariables = mapOf("failedChecks" to AssignmentKindAst.ADD),
            )
        )
        assertTrue(actual = withScope.isValid, message = "unexpected: ${withScope.diagnostics}")
    }

    @Test
    fun `a missing description is a warning, so the rule is still valid`() {
        val rule = """
            rule "no-description" {
              when
                purpose contains "rent"
              then
                label "y"
            }
        """.trimIndent()
        val outcome = assertIs<RuleValidationOutcome.Completed>(value = run(rule = rule))

        assertTrue(actual = outcome.isValid, message = "a warning must not block compilation")
        assertTrue(actual = outcome.diagnostics.any { it.severity == Severity.WARNING })
    }
}
