package ui.tester

import kotlin.test.Test
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
    fun `positive match returns Matched with actions`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = POSITIVE_INPUT,
        )
        assertIs<SimulationOutcome.Matched>(result.outcome)
        val matched = result.outcome as SimulationOutcome.Matched
        assertTrue(matched.actions.any { it.contains("rent") })
    }

    @Test
    fun `negative no-match returns NotMatched`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_RULE,
            ruleId = "rent-payment",
            inputJson = NEGATIVE_INPUT,
        )
        assertIs<SimulationOutcome.NotMatched>(result.outcome)
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
        assertIs<SimulationOutcome.InvalidJson>(result.outcome)
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
        assertIs<SimulationOutcome.ValidationFailed>(result.outcome)
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
        assertTrue(result.traceRows.isNotEmpty(), "Expected trace rows for a matched rule")
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
        assertIs<SimulationOutcome.ValidationFailed>(result.outcome)
    }
}
