package ui

import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ui.builder.BuilderConditionNode
import ui.builder.BuilderEditorState
import ui.builder.BuilderRule
import ui.builder.generateUniqueRuleId
import ui.builder.isBuilderStateStale
import ui.builder.isLocked
import ui.builder.ruleId
import ui.editor.rules.StatusKind
import ui.tester.RuleResult
import ui.tester.SimulationOutcome
import ui.tester.runStatusKind
import ui.tester.runStatusMessage
import ui.workbench.model.CatalogRuleStatus
import ui.workbench.ruleTreeStatusFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characterization tests for the helpers that lived inside the editor screen.
 *
 * Written before those helpers moved, and asserting what they did at that moment — including the
 * parts that look like bugs. Anything surprising here is deliberate and load-bearing; the notes say
 * which, so a later change is a decision rather than an accident.
 */
class RuleEditorHelpersTest {

    // ── generateUniqueRuleId ──────────────────────────────────────────────────

    @Test
    fun `the first rule in an empty buffer is rule-1`() {
        assertEquals(expected = "rule-1", actual = generateUniqueRuleId(existingIds = emptySet()))
    }

    /**
     * Numbering starts at `size + 1`, not at the first free number. With `rule-1` and `rule-7`
     * present the next id is `rule-3`, not `rule-2` — ids are unique but not sequential.
     */
    @Test
    fun `numbering starts from the count, so ids are unique but not sequential`() {
        assertEquals(expected = "rule-3", actual = generateUniqueRuleId(existingIds = setOf("rule-1", "rule-7")))
    }

    @Test
    fun `a taken candidate is skipped until one is free`() {
        assertEquals(
            expected = "rule-4",
            actual = generateUniqueRuleId(existingIds = setOf("rule-1", "rule-2", "rule-3")),
        )
    }

    // ── ruleTreeStatusFor ─────────────────────────────────────────────────────

    private fun error(message: String) = ValidationDiagnostic(severity = Severity.ERROR, message = message)
    private fun warning(message: String) = ValidationDiagnostic(severity = Severity.WARNING, message = message)

    @Test
    fun `an error naming the rule marks it invalid wherever the rule lives`() {
        assertEquals(
            expected = CatalogRuleStatus.INVALID,
            actual = ruleTreeStatusFor(
                ruleId = "vip",
                description = "has one",
                relativePath = "other.rule",
                currentFile = "current.rule",
                diagnostics = listOf(error("Unknown field in vip")),
            ),
        )
    }

    /**
     * Any error in the file being edited invalidates every rule in it, because a parse error rarely
     * names the rules it breaks. Rules in *other* files do not get that fallback.
     */
    @Test
    fun `an unrelated error invalidates the open file's rules but not another file's`() {
        val diagnostics = listOf(error("Syntax error near '{'"))
        assertEquals(
            expected = CatalogRuleStatus.INVALID,
            actual = ruleTreeStatusFor(
                ruleId = "vip", description = "has one", relativePath = "current.rule",
                currentFile = "current.rule", diagnostics = diagnostics,
            ),
        )
        assertEquals(
            expected = CatalogRuleStatus.VALID,
            actual = ruleTreeStatusFor(
                ruleId = "vip", description = "has one", relativePath = "other.rule",
                currentFile = "current.rule", diagnostics = diagnostics,
            ),
        )
    }

    @Test
    fun `a rule without a description is a draft, not an error`() {
        assertEquals(
            expected = CatalogRuleStatus.DRAFT,
            actual = ruleTreeStatusFor(
                ruleId = "vip", description = "  ", relativePath = "a.rule",
                currentFile = "a.rule", diagnostics = emptyList(),
            ),
        )
    }

    @Test
    fun `warnings never make a rule invalid`() {
        assertEquals(
            expected = CatalogRuleStatus.VALID,
            actual = ruleTreeStatusFor(
                ruleId = "vip", description = "has one", relativePath = "a.rule",
                currentFile = "a.rule", diagnostics = listOf(warning("Rule 'vip' has no description")),
            ),
        )
    }

    // ── BuilderRule helpers ───────────────────────────────────────────────────

    private fun supported(id: String) = BuilderRule.Supported(
        id = id,
        conditionNodes = listOf(
            BuilderConditionNode.Condition(nodeId = "c1", field = "amount", operator = "equals", value = "1"),
        ),
        actions = emptyList(),
    )

    @Test
    fun `only a supported rule is editable`() {
        assertFalse(actual = supported(id = "r").isLocked())
        assertTrue(actual = BuilderRule.Unsupported(id = "r", reason = "aggregate").isLocked())
        assertTrue(actual = BuilderRule.None.isLocked())
    }

    @Test
    fun `the empty rule has an empty id, which is what marks it as no selection`() {
        assertEquals(expected = "r", actual = supported(id = "r").ruleId())
        assertEquals(expected = "r", actual = BuilderRule.Unsupported(id = "r", reason = "x").ruleId())
        assertEquals(expected = "", actual = BuilderRule.None.ruleId())
    }

    // ── isBuilderStateStale ───────────────────────────────────────────────────

    @Test
    fun `a null state is never stale, so nothing is rebuilt before there is anything to rebuild`() {
        assertFalse(actual = isBuilderStateStale(existing = null, currentFullText = "anything"))
    }

    @Test
    fun `a locked state is never stale, because the builder cannot have edited it`() {
        val locked = BuilderEditorState.fromBuilderRule(BuilderRule.Unsupported(id = "r", reason = "aggregate"))
        assertFalse(actual = isBuilderStateStale(existing = locked, currentFullText = ""))
    }

    @Test
    fun `a state whose generated DSL is absent from the buffer is stale`() {
        val state = BuilderEditorState.fromBuilderRule(supported(id = "r"))
        assertTrue(actual = isBuilderStateStale(existing = state, currentFullText = "rule \"other\" { }"))
    }

    @Test
    fun `a state whose generated DSL is contained in the buffer is fresh`() {
        val state = BuilderEditorState.fromBuilderRule(supported(id = "r"))
        val generated = ui.builder.BuilderToRuleDsl.generate(state = state)
        requireNotNull(generated) { "the fixture must be generatable, otherwise the test proves nothing" }

        assertFalse(actual = isBuilderStateStale(existing = state, currentFullText = "# leading\n$generated\n"))
    }

    // ── run status ────────────────────────────────────────────────────────────

    private fun result(id: String, matched: Boolean, actions: List<String> = emptyList()) = RuleResult(
        ruleId = id, matched = matched, actions = actions, traceRows = emptyList(),
    )

    @Test
    fun `a run that matched nothing reads as idle, not as success`() {
        val outcome = SimulationOutcome.Completed(ruleResults = listOf(result(id = "a", matched = false)))
        assertEquals(expected = StatusKind.IDLE, actual = runStatusKind(outcome = outcome))
    }

    @Test
    fun `a run with at least one match is a success`() {
        val outcome = SimulationOutcome.Completed(
            ruleResults = listOf(
                result(id = "a", matched = true, actions = listOf("flag \"x\"", "reason \"y\"")),
                result(id = "b", matched = false),
            ),
        )
        assertEquals(expected = StatusKind.SUCCESS, actual = runStatusKind(outcome = outcome))
        assertEquals(expected = "1 of 2 rules matched — 2 action(s)", actual = runStatusMessage(outcome = outcome))
    }

    @Test
    fun `a run that could not start reports why, as an error`() {
        val invalid = SimulationOutcome.InvalidJson(reason = "Unexpected token")
        assertEquals(expected = StatusKind.ERROR, actual = runStatusKind(outcome = invalid))
        assertEquals(
            expected = "Test not run: invalid JSON — Unexpected token",
            actual = runStatusMessage(outcome = invalid),
        )

        val failed = SimulationOutcome.ValidationFailed(reason = "2 errors")
        assertEquals(expected = StatusKind.ERROR, actual = runStatusKind(outcome = failed))
        assertEquals(expected = "Test not run: 2 errors", actual = runStatusMessage(outcome = failed))
    }

    @Test
    fun `an idle run reads as ready`() {
        assertEquals(expected = StatusKind.IDLE, actual = runStatusKind(outcome = SimulationOutcome.Idle))
        assertEquals(expected = "Ready", actual = runStatusMessage(outcome = SimulationOutcome.Idle))
    }
}
