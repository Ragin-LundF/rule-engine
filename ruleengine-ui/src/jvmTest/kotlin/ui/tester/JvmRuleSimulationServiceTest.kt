package ui.tester

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
    argTypes: [text]
""".trimIndent()

private val RENT_RULE = """
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 500
  then
    label "rent"
}
""".trimIndent()

/**
 * Two rules where exactly one fires — the shape of a real rule set, whose rules come in mutually
 * exclusive pairs so roughly half of them are expected to stay silent.
 */
private val RENT_AND_COFFEE_RULES = """
rule "rent-payment" {
  when
    purpose contains "rent"
    and amount >= 500
  then
    label "rent"
}

rule "coffee-payment" {
  when
    purpose contains "coffee"
  then
    label "coffee"
}
""".trimIndent()

/**
 * Against [POSITIVE_INPUT] the amount condition holds and the purpose condition does not, so the
 * rule does not fire but part of it did — the case the PARTIAL state exists to make visible.
 *
 * The amount comparison is deliberately first in evaluation order: `AndExpression` sorts children by
 * cost (VERY_CHEAP for a decimal comparison, MEDIUM for a text contains) and stops at the first
 * failure, so a rule only ever reaches PARTIAL when a condition that holds is evaluated before one
 * that does not. See [SHORT_CIRCUITED_RULE] for the other order.
 */
private val PARTIAL_RULE = """
rule "cheap-coffee-payment" {
  when
    amount >= 500
    and purpose contains "coffee"
  then
    label "cheap-coffee"
}
""".trimIndent()

/**
 * The mirror image: the failing condition is the cheap one, so evaluation stops before the condition
 * that would have held is ever reached. Nothing is recorded as true, so this reports NO_MATCH.
 */
private val SHORT_CIRCUITED_RULE = """
rule "large-rent-payment" {
  when
    amount >= 5000
    and purpose contains "rent"
  then
    label "large-rent"
}
""".trimIndent()

private val POSITIVE_INPUT = """
{
  "purpose": "Monthly rent payment",
  "amount": 750.00,
  "country": "DE"
}
""".trimIndent()

private val NEGATIVE_INPUT = """
{
  "purpose": "Coffee shop",
  "amount": 5.50
}
""".trimIndent()

class JvmRuleSimulationServiceTest {

    private val service = JvmRuleSimulationService()

    @Test
    fun `positive match reports the rule as matched with its actions`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        assertEquals(expected = 1, actual = outcome.ruleResults.size)
        val ruleResult = outcome.ruleResults.single()
        assertEquals(expected = "rent-payment", actual = ruleResult.ruleId)
        assertTrue(actual = ruleResult.matched)
        assertTrue(actual = ruleResult.actions.any { it.contains(other = "rent") })
    }

    @Test
    fun `negative no-match still reports the rule, with no actions`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = NEGATIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val ruleResult = outcome.ruleResults.single()
        assertEquals(expected = "rent-payment", actual = ruleResult.ruleId)
        assertEquals(expected = false, actual = ruleResult.matched)
        assertEquals(expected = emptyList(), actual = ruleResult.actions)
        assertEquals(expected = 0, actual = outcome.matchedCount)
    }

    /**
     * The regression this whole per-rule model exists for: running every rule used to report the first
     * match only, so the other rules' actions were dropped and their failing conditions showed up in a
     * single flat trace with no way to tell which rule they belonged to.
     */
    @Test
    fun `running all rules reports every rule in declaration order`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_AND_COFFEE_RULES,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        assertEquals(
            expected = listOf("rent-payment", "coffee-payment"),
            actual = outcome.ruleResults.map { it.ruleId },
        )
        assertEquals(expected = 1, actual = outcome.matchedCount)
        assertEquals(expected = 1, actual = outcome.actionCount)
    }

    @Test
    fun `running all rules keeps actions on the rule that emitted them`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_AND_COFFEE_RULES,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val rent = outcome.ruleResults.single { it.ruleId == "rent-payment" }
        val coffee = outcome.ruleResults.single { it.ruleId == "coffee-payment" }

        assertTrue(actual = rent.matched)
        assertTrue(actual = rent.actions.any { it.contains(other = "rent") })
        assertEquals(expected = false, actual = coffee.matched)
        assertEquals(expected = emptyList(), actual = coffee.actions)
    }

    @Test
    fun `each rule carries only its own trace rows`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_AND_COFFEE_RULES,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val rent = outcome.ruleResults.single { it.ruleId == "rent-payment" }
        val coffee = outcome.ruleResults.single { it.ruleId == "coffee-payment" }

        assertEquals(expected = 2, actual = rent.traceRows.size)
        assertTrue(
            actual = rent.traceRows.none { it.label.contains(other = "coffee") },
            message = "The rent rule must not be shown the coffee rule's conditions: ${rent.traceRows}",
        )
        assertEquals(expected = 1, actual = coffee.traceRows.size)
        assertTrue(actual = coffee.traceRows.single().label.contains(other = "coffee"))
        assertEquals(expected = false, actual = coffee.traceRows.single().result)
    }

    @Test
    fun `a rule that fired is MATCHED and one where nothing held is NO_MATCH`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_AND_COFFEE_RULES,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        assertEquals(
            expected = RuleMatchStatus.MATCHED,
            actual = outcome.ruleResults.single { it.ruleId == "rent-payment" }.status,
        )
        assertEquals(
            expected = RuleMatchStatus.NO_MATCH,
            actual = outcome.ruleResults.single { it.ruleId == "coffee-payment" }.status,
        )
    }

    @Test
    fun `a rule with one condition held and one failed is PARTIAL`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = PARTIAL_RULE,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val ruleResult = outcome.ruleResults.single()
        assertEquals(expected = false, actual = ruleResult.matched)
        assertEquals(expected = RuleMatchStatus.PARTIAL, actual = ruleResult.status)
        assertEquals(expected = 1, actual = ruleResult.traceRows.count { it.result })
        assertEquals(expected = 1, actual = ruleResult.traceRows.count { !it.result })
    }

    /**
     * The status describes what the expanded trace actually shows, and a short-circuited `and` never
     * evaluates — so never records — the condition that would have held. Keeping the two in step is
     * the point: a rule row must never read "partial" over a trace with nothing green in it.
     */
    @Test
    fun `a rule short-circuited before any condition held is NO_MATCH`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = SHORT_CIRCUITED_RULE,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val ruleResult = outcome.ruleResults.single()
        assertEquals(expected = RuleMatchStatus.NO_MATCH, actual = ruleResult.status)
        assertTrue(
            actual = ruleResult.traceRows.none { it.result },
            message = "Nothing held, so nothing may be green: ${ruleResult.traceRows}",
        )
    }

    /**
     * Aggregate and field-to-field comparisons are not instrumented, so most real rules produce no
     * trace rows at all. Those must land in NO_MATCH rather than throwing or reporting PARTIAL.
     */
    @Test
    fun `a non-matching rule with no trace rows is NO_MATCH`() {
        val ruleResult = RuleResult(
            ruleId = "aggregate-only",
            matched = false,
            actions = emptyList(),
            traceRows = emptyList(),
        )
        assertEquals(expected = RuleMatchStatus.NO_MATCH, actual = ruleResult.status)
    }

    @Test
    fun `a match stays MATCHED even when a condition in its trace is false`() {
        val ruleResult = RuleResult(
            ruleId = "or-rule",
            matched = true,
            actions = listOf("""label "x""""),
            traceRows = listOf(
                TraceRow(label = "purpose contains rent", result = true),
                TraceRow(label = "purpose contains coffee", result = false),
            ),
        )
        assertEquals(expected = RuleMatchStatus.MATCHED, actual = ruleResult.status)
    }

    @Test
    fun `invalid JSON returns InvalidJson outcome`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = "not valid json {{{",
        )
        assertIs<SimulationOutcome.InvalidJson>(value = result.outcome)
    }

    @Test
    fun `invalid rule returns ValidationFailed outcome`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = """
                rule "bad" {
                  when
                    unknownField contains "x"
                  then
                    label "x"
                }
            """.trimIndent(),
            ruleId = "bad",
            inputJson = POSITIVE_INPUT,
        )
        assertIs<SimulationOutcome.ValidationFailed>(value = result.outcome)
    }

    @Test
    fun `positive match produces non-empty trace rows`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        assertTrue(
            actual = outcome.ruleResults.single().traceRows.isNotEmpty(),
            message = "Expected trace rows for a matched rule",
        )
    }

    @Test
    fun `blank schema returns ValidationFailed`() {
        val result = service.simulate(
            schemaText = "",
            actionsText = "",
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = POSITIVE_INPUT,
        )
        assertIs<SimulationOutcome.ValidationFailed>(value = result.outcome)
    }

    @Test
    fun `an unknown rule id is reported instead of silently running another rule`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_AND_COFFEE_RULES,
            ruleId = "does-not-exist",
            inputJson = POSITIVE_INPUT,
        )
        assertIs<SimulationOutcome.ValidationFailed>(value = result.outcome)
    }
}
