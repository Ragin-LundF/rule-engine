package ui.workbench

import ruleengine.core.errors.Severity
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.mode.RuleMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the Inspector describes.
 *
 * The panel had no reachable rule selection at all — nothing dispatched `SelectRule` — so these pin
 * the two decisions that made it reachable: which rule each mode resolves to, and the diagnostics
 * narrowing that stops a rule reporting its neighbours' errors as its own.
 */
class InspectorSelectionTest {

    private val text = """
        rule "first" {
          when
            amount >= 5
          then
            flag "small"
        }

        rule "second" {
          when
            amount >= 50
          then
            flag "large"
        }
    """.trimIndent()

    private val ids = listOf("first", "second")

    private fun diagnostic(line: Int?) =
        UiDiagnostic(severity = Severity.ERROR, message = "line $line", line = line)

    private fun select(
        mode: RuleMode,
        caret: Int = 0,
        builderRuleId: String = "",
        diagnostics: List<UiDiagnostic> = emptyList(),
    ) = inspectorSelectionFor(
        ruleMode = mode,
        ruleText = text,
        ruleIds = ids,
        caret = caret,
        builderRuleId = builderRuleId,
        diagnostics = diagnostics,
    )

    // ── which rule ────────────────────────────────────────────────────────────

    @Test
    fun `code mode follows the caret, not the builder's selection`() {
        // The case the counts used to get wrong: the builder holds one rule open while the caret sits
        // in another, and the panel used to name one and count the other.
        val selection = select(
            mode = RuleMode.CODE,
            caret = text.indexOf("flag \"large\""),
            builderRuleId = "first",
        )

        assertEquals(expected = "second", actual = selection.ruleId)
    }

    @Test
    fun `code mode falls back to the builder's rule when the caret is between blocks`() {
        val gap = text.indexOf("}\n\nrule") + 2

        assertEquals(expected = "first", actual = select(RuleMode.CODE, caret = gap, builderRuleId = "first").ruleId)
    }

    @Test
    fun `builder mode ignores the caret entirely`() {
        // The caret is inside "second" here; builder mode must still report the selected rule, because
        // that is the one whose card is on screen.
        val selection = select(
            mode = RuleMode.BUILDER,
            caret = text.indexOf("flag \"large\""),
            builderRuleId = "first",
        )

        assertEquals(expected = "first", actual = selection.ruleId)
    }

    @Test
    fun `a blank builder rule id is no selection rather than a rule named empty`() {
        assertNull(actual = select(RuleMode.BUILDER, builderRuleId = "").ruleId)
        assertNull(actual = select(RuleMode.TABLE, builderRuleId = "").ruleId)
    }

    // ── which diagnostics ─────────────────────────────────────────────────────

    @Test
    fun `diagnostics are narrowed to the inspected rule's own lines`() {
        // "first" spans lines 1..6, "second" lines 8..13.
        val all = listOf(diagnostic(line = 3), diagnostic(line = 10))

        val selection = select(RuleMode.BUILDER, builderRuleId = "first", diagnostics = all)

        assertEquals(expected = listOf(3), actual = selection.diagnostics.map { it.line })
    }

    @Test
    fun `a diagnostic with no line is kept, because nothing says it belongs elsewhere`() {
        val all = listOf(diagnostic(line = null), diagnostic(line = 10))

        val selection = select(RuleMode.BUILDER, builderRuleId = "first", diagnostics = all)

        assertEquals(expected = listOf(null), actual = selection.diagnostics.map { it.line })
    }

    @Test
    fun `with no rule selected every diagnostic is kept rather than none`() {
        val all = listOf(diagnostic(line = 3), diagnostic(line = 10))

        assertEquals(
            expected = all,
            actual = select(RuleMode.BUILDER, builderRuleId = "", diagnostics = all).diagnostics,
        )
    }

    @Test
    fun `a selected rule that is not in the text keeps every diagnostic`() {
        // Selection outlives the text it points into — a rule can be selected and then renamed away.
        // Narrowing against a block that no longer exists would silently hide every error.
        val all = listOf(diagnostic(line = 3))

        assertEquals(
            expected = all,
            actual = select(RuleMode.BUILDER, builderRuleId = "renamed-away", diagnostics = all).diagnostics,
        )
    }
}
