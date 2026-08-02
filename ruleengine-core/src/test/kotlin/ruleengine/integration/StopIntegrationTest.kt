package ruleengine.integration

import ruleengine.compiler.Compiler
import ruleengine.compiler.Validator
import ruleengine.core.domain.dto.EvaluationResult
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.core.domain.dto.action.ActionArgType
import ruleengine.core.domain.dto.action.ActionDefinition
import ruleengine.core.domain.dto.action.ActionSchema
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end behaviour of `stop`: the branch's own output is collected, then the rules declared after
 * it are not evaluated at all.
 */
class StopIntegrationTest {

    private val schema = FieldSchema(
        name = "orders",
        fields = mapOf(
            FieldId(value = "amount") to FieldDefinition(
                id = FieldId(value = "amount"),
                type = FieldType.DECIMAL
            ),
            FieldId(value = "country") to FieldDefinition(
                id = FieldId(value = "country"),
                type = FieldType.TEXT
            )
        )
    )

    private val actionSchema = ActionSchema(
        actions = mapOf(
            "label" to ActionDefinition(name = "label", argTypes = listOf(ActionArgType.STRING)),
            "decision" to ActionDefinition(name = "decision", argTypes = listOf(ActionArgType.STRING)),
        )
    )

    @Test
    fun `a matching rule that stops keeps its own output and ends the run`() {
        val result = evaluate(rules = blockedThenTier, "country" to "XX", "amount" to 5000)

        assertEquals(expected = listOf("blocked"), actual = result.matches.map { it.ruleId })
        assertEquals(expected = listOf("reject"), actual = result.matches.single().actions.single().arguments)
        assertEquals(expected = "blocked", actual = result.stoppedBy)
    }

    @Test
    fun `the rules after the stop run normally when it does not fire`() {
        val result = evaluate(rules = blockedThenTier, "country" to "DE", "amount" to 5000)

        assertEquals(expected = listOf("tier"), actual = result.matches.map { it.ruleId })
        assertNull(actual = result.stoppedBy)
    }

    @Test
    fun `a stop in an else block ends the run when the condition does not hold`() {
        val rules = """
            rule "must-be-known-country" {
              description "d"
              when
                country equals "DE"
              then
                label "known"
              else
                decision "reject"
                stop
            }
            rule "later" {
              description "d"
              when
                amount > 0
              then
                label "priced"
            }
        """.trimIndent()

        val stopped = evaluate(rules = rules, "country" to "XX", "amount" to 10)
        assertEquals(expected = listOf("must-be-known-country"), actual = stopped.matches.map { it.ruleId })
        assertEquals(expected = RuleBranch.ELSE, actual = stopped.matches.single().branch)
        assertEquals(expected = "must-be-known-country", actual = stopped.stoppedBy)

        val notStopped = evaluate(rules = rules, "country" to "DE", "amount" to 10)
        assertEquals(
            expected = listOf("must-be-known-country", "later"),
            actual = notStopped.matches.map { it.ruleId },
        )
        assertNull(actual = notStopped.stoppedBy)
    }

    /** The `then` branch stops, so a record taking the `else` branch must carry on. */
    @Test
    fun `a stop on one branch leaves the other branch running`() {
        val rules = """
            rule "shortcut" {
              description "d"
              when
                amount > 1000
              then
                label "big"
                stop
              else
                label "small"
            }
            rule "later" {
              description "d"
              when
                amount > 0
              then
                label "priced"
            }
        """.trimIndent()

        val stopped = evaluate(rules = rules, "amount" to 5000)
        assertEquals(expected = listOf("shortcut"), actual = stopped.matches.map { it.ruleId })
        assertEquals(expected = "shortcut", actual = stopped.stoppedBy)

        val carriedOn = evaluate(rules = rules, "amount" to 10)
        assertEquals(expected = listOf("shortcut", "later"), actual = carriedOn.matches.map { it.ruleId })
        assertNull(actual = carriedOn.stoppedBy)
    }

    @Test
    fun `a stop-only then block ends the run without producing output`() {
        val result = evaluate(
            rules = """
                rule "guard" {
                  description "d"
                  when
                    country equals "XX"
                  then
                    stop
                }
                rule "later" {
                  description "d"
                  when
                    amount > 0
                  then
                    label "priced"
                }
            """.trimIndent(),
            "country" to "XX",
            "amount" to 10,
        )

        assertTrue(actual = result.matches.isEmpty(), message = "unexpected matches: ${result.matches}")
        assertEquals(expected = "guard", actual = result.stoppedBy)
    }

    @Test
    fun `a variable published before the stop is still in the result`() {
        val result = evaluate(
            rules = """
                rule "totals" {
                  description "d"
                  when
                    amount > 0
                  then
                    set doubled = amount * 2
                    stop
                }
                rule "never-runs" {
                  description "d"
                  when
                    ${'$'}doubled > 0
                  then
                    label "seen"
                }
            """.trimIndent(),
            "amount" to 50,
        )

        assertEquals(expected = listOf("totals"), actual = result.matches.map { it.ruleId })
        assertEquals(expected = java.math.BigDecimal("100"), actual = result.variables["doubled"])
    }

    // ── guards ────────────────────────────────────────────────────────────────

    @Test
    fun `an action named stop in the action schema is an error`() {
        val schemaWithKeyword = ActionSchema(
            actions = mapOf("stop" to ActionDefinition(name = "stop", argTypes = listOf(ActionArgType.STRING)))
        )

        val errors = Validator.validate(
            asts = emptyList(),
            schema = schema,
            actions = schemaWithKeyword,
        ).diagnostics.filter { it.severity == Severity.ERROR }

        assertEquals(expected = 1, actual = errors.size)
        assertContains(charSequence = errors.single().message, other = "'stop' is a rule keyword")
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private val blockedThenTier = """
        rule "blocked" {
          description "An order from a sanctioned country is rejected outright; nothing else applies."
          when
            country equals "XX"
          then
            decision "reject"
            stop
        }
        rule "tier" {
          description "d"
          when
            amount >= 1000
          then
            label "high"
        }
    """.trimIndent()

    private fun evaluate(rules: String, vararg fields: Pair<String, Any?>): EvaluationResult {
        val asts = Parser(input = rules).parseRules()
        val validation = Validator.validate(asts = asts, schema = schema, actions = actionSchema)
        assertTrue(
            actual = validation.diagnostics.none { it.severity == Severity.ERROR },
            message = "unexpected errors: ${validation.diagnostics}",
        )
        val prepared = PreparedRuleContext.prepare(ctx = RuleContext.of(*fields), schema = schema)
        return RuleEngine(compiledRules = Compiler.compileRules(asts = asts, schema = schema))
            .evaluate(prepared = prepared)
    }
}
