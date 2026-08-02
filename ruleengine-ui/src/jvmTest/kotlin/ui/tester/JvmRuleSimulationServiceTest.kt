package ui.tester

import ruleengine.core.domain.dto.RuleBranch
import ruleengine.evaluator.trace.dto.NodeType
import ui.tester.model.RuleMatchStatus
import ui.tester.model.RuleResult
import ui.tester.model.SimulationOutcome
import ui.tester.model.TraceNode
import ui.tester.model.TraceRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
  items:
    type: collection
    fields:
      price:
        type: decimal
""".trimIndent()

/**
 * An aggregate comparison rather than a plain condition, because only `ComparisonCompiledExpression`
 * records the value it actually computed — a plain `amount >= 500` leaf reports no `actual`.
 */
private val AGGREGATE_RULE = """
rule "big-basket" {
  when
    sum(items.price) > 100
  then
    label "big"
}
""".trimIndent()

private val BASKET_INPUT = """
{
  "purpose": "Groceries",
  "amount": 105.00,
  "items": [
    { "price": 60.00 },
    { "price": 45.00 }
  ]
}
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

private val STOPPING_RULES = """
rule "stopper" {
  when
    purpose contains "rent"
  then
    label "rent"
    stop
}

rule "after" {
  when
    amount >= 500
  then
    label "large"
}
""".trimIndent()

private val ELSE_BRANCH_RULE = """
rule "rent-or-other" {
  when
    purpose contains "rent"
    and amount >= 500
  then
    label "rent"
  else
    label "other"
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
     * A rule can still end up with no rows — short-circuited before any condition was evaluated.
     * Those must land in NO_MATCH rather than throwing or reporting PARTIAL.
     */
    @Test
    fun `a non-matching rule with no trace rows is NO_MATCH`() {
        val ruleResult = RuleResult(
            ruleId = "never-evaluated",
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

    /**
     * The regression this guards: the roster derived `matched` from mere presence in
     * `EvaluationResult.matches`, which an `else` branch also puts a rule into — so a rule whose
     * condition was false would have been reported as having matched.
     */
    @Test
    fun `a rule whose else branch fired is ELSE_MATCHED and not matched`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = ELSE_BRANCH_RULE,
            ruleId = "",
            inputJson = NEGATIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val ruleResult = outcome.ruleResults.single()
        assertEquals(expected = false, actual = ruleResult.matched)
        assertEquals(expected = RuleBranch.ELSE, actual = ruleResult.branch)
        assertEquals(expected = RuleMatchStatus.ELSE_MATCHED, actual = ruleResult.status)
        assertEquals(expected = listOf("""label "other""""), actual = ruleResult.actions)
    }

    @Test
    fun `the same rule reports the then branch when its condition holds`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = ELSE_BRANCH_RULE,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        val ruleResult = outcome.ruleResults.single()
        assertEquals(expected = true, actual = ruleResult.matched)
        assertEquals(expected = RuleBranch.THEN, actual = ruleResult.branch)
        assertEquals(expected = RuleMatchStatus.MATCHED, actual = ruleResult.status)
        assertEquals(expected = listOf("""label "rent""""), actual = ruleResult.actions)
    }

    /** ELSE_MATCHED wins over PARTIAL: the rule has a definite answer, not a near miss. */
    @Test
    fun `an else-fired rule with a partially true trace is still ELSE_MATCHED`() {
        val ruleResult = RuleResult(
            ruleId = "tier",
            matched = false,
            branch = RuleBranch.ELSE,
            actions = listOf("""label "low""""),
            traceRows = listOf(
                TraceRow(label = "purpose contains rent", result = true),
                TraceRow(label = "amount gte 5000", result = false),
            ),
        )
        assertEquals(expected = RuleMatchStatus.ELSE_MATCHED, actual = ruleResult.status)
    }

    /**
     * The rules after a `stop` were never tested, so nothing is known about them. Reporting them as
     * "no match" would be a claim the run never made.
     */
    @Test
    fun `rules after a stop are NOT_EVALUATED rather than no match`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = STOPPING_RULES,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        assertEquals(
            expected = RuleMatchStatus.MATCHED,
            actual = outcome.ruleResults.single { it.ruleId == "stopper" }.status,
        )
        assertEquals(
            expected = RuleMatchStatus.NOT_EVALUATED,
            actual = outcome.ruleResults.single { it.ruleId == "after" }.status,
        )
    }

    @Test
    fun `the rules after a stop report normally when it does not fire`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = STOPPING_RULES,
            ruleId = "",
            inputJson = NEGATIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        assertTrue(
            actual = outcome.ruleResults.none { it.status == RuleMatchStatus.NOT_EVALUATED },
            message = "nothing halted the run: ${outcome.ruleResults.map { it.ruleId to it.status }}",
        )
    }

    /** NOT_EVALUATED wins over every other status, including a trace left by an earlier run. */
    @Test
    fun `a not-evaluated rule is never reported as partial`() {
        val ruleResult = RuleResult(
            ruleId = "after",
            matched = false,
            notEvaluated = true,
            actions = emptyList(),
            traceRows = listOf(TraceRow(label = "purpose contains rent", result = true)),
        )
        assertEquals(expected = RuleMatchStatus.NOT_EVALUATED, actual = ruleResult.status)
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

    // ── trace tree ────────────────────────────────────────────────────────────

    private fun treeOf(ruleText: String, ruleId: String, inputJson: String): TraceNode {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = ruleText,
            ruleId = ruleId,
            inputJson = inputJson,
        )
        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        return assertNotNull(actual = outcome.ruleResults.single().traceTree)
    }

    private fun conditionCount(node: TraceNode): Int {
        if (node.type == NodeType.CONDITION) {
            return 1
        }
        return node.children.sumOf { child -> conditionCount(node = child) }
    }

    /**
     * The structure the flat row list cannot carry: the rule node, the `and` beneath it, and the two
     * conditions beneath that. The results view only ever needed the leaves; the trace diagram draws
     * the nesting, so it has to survive the mapping out of the core's `DecisionTree`.
     */
    @Test
    fun `the trace tree keeps the rule, its and node and the conditions beneath it`() {
        val tree = treeOf(ruleText = RENT_RULE, ruleId = "rent-payment", inputJson = POSITIVE_INPUT)

        assertEquals(expected = NodeType.RULE, actual = tree.type)
        assertEquals(expected = "rent-payment", actual = tree.label)
        assertTrue(actual = tree.result)

        val and = tree.children.single()
        assertEquals(expected = NodeType.AND, actual = and.type)
        assertTrue(actual = and.result)
        assertEquals(expected = 2, actual = and.children.size)
        assertTrue(
            actual = and.children.all { child -> child.type == NodeType.CONDITION },
            message = "Expected two condition leaves, got: ${and.children.map { it.type }}",
        )
    }

    /**
     * The reason [TraceNode.result] is not nullable. `AndExpression` returns on the first false child
     * without ever calling the collector for the rest, so a condition that was not evaluated is absent
     * from the tree rather than present and undecided. Here `purpose contains "rent"` would have held,
     * but the cheaper amount check failed first and it was never reached — so the `and` must carry one
     * child, not two with one marked unknown.
     */
    @Test
    fun `a short-circuited and records only the child it actually evaluated`() {
        val tree = treeOf(ruleText = SHORT_CIRCUITED_RULE, ruleId = "large-rent-payment", inputJson = POSITIVE_INPUT)

        val and = tree.children.single()
        assertEquals(expected = NodeType.AND, actual = and.type)
        assertEquals(expected = false, actual = and.result)
        assertEquals(
            expected = 1,
            actual = and.children.size,
            message = "The unevaluated condition must be absent, not recorded: ${and.children.map { it.label }}",
        )
        assertEquals(expected = false, actual = and.children.single().result)
    }

    /** The rows are derived from the tree, so they can never disagree about what was evaluated. */
    @Test
    fun `the flat rows are exactly the condition leaves of the tree`() {
        val result = service.simulate(
            schemaText = SCHEMA_TEXT,
            actionsText = ACTIONS_TEXT,
            ruleText = RENT_AND_COFFEE_RULES,
            ruleId = "",
            inputJson = POSITIVE_INPUT,
        )

        val outcome = assertIs<SimulationOutcome.Completed>(value = result.outcome)
        outcome.ruleResults.forEach { ruleResult ->
            val tree = assertNotNull(actual = ruleResult.traceTree)
            assertEquals(
                expected = conditionCount(node = tree),
                actual = ruleResult.traceRows.size,
                message = "Rows and tree disagree for ${ruleResult.ruleId}",
            )
        }
    }

    /**
     * The value beside the expected one is what makes the trace diagram worth drawing, so the
     * passthrough out of `DecisionNode.actual` needs a test of its own. `sum(items.price)` over 60 and
     * 45 must report what it computed, not just that the comparison held.
     */
    @Test
    fun `a comparison leaf carries the value it actually computed`() {
        val tree = treeOf(ruleText = AGGREGATE_RULE, ruleId = "big-basket", inputJson = BASKET_INPUT)
        val comparison = tree.children.single()

        assertEquals(expected = NodeType.CONDITION, actual = comparison.type)
        assertTrue(actual = comparison.result)
        assertEquals(expected = "105.0", actual = comparison.actual)
    }
}
