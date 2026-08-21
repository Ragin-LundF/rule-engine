package ui.workbench

import ui.ruleIdAtCaret
import ui.ruleLineRange
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.mode.RuleMode

/**
 * What the Inspector describes, derived from the editor's state.
 *
 * Extracted from the editor screen for the same reason `WorkbenchCatalogs` was: the screen should
 * read as layout, and this is data preparation with branches in it. Being pure also makes it
 * testable, which the equivalent code inside a `remember` block was not.
 */
internal data class InspectorSelection(
    /** The rule the panel names, or null when nothing is selected. */
    val ruleId: String?,
    /** Diagnostics belonging to [ruleId] alone. */
    val diagnostics: List<UiDiagnostic>,
)

/**
 * Resolves the selection for [ruleMode].
 *
 * In every mode but code there is a rule selected on screen — the tree, the table, the builder's own
 * header all show it — and [builderRuleId] is that rule. Code mode has no such selection, so the
 * caret is the selection; falling back to [builderRuleId] when the caret sits between two blocks
 * keeps the panel from blanking on a click in the gap.
 */
internal fun inspectorSelectionFor(
    ruleMode: RuleMode,
    ruleText: String,
    ruleIds: List<String>,
    caret: Int,
    builderRuleId: String,
    diagnostics: List<UiDiagnostic>,
): InspectorSelection {
    val fromBuilder = builderRuleId.takeIf { it.isNotBlank() }
    val ruleId = if (ruleMode == RuleMode.CODE) {
        ruleIdAtCaret(fullText = ruleText, ruleIds = ruleIds, caret = caret) ?: fromBuilder
    } else {
        fromBuilder
    }
    return InspectorSelection(
        ruleId = ruleId,
        diagnostics = diagnosticsForRule(diagnostics = diagnostics, ruleText = ruleText, ruleId = ruleId),
    )
}

/**
 * Narrows [diagnostics] to the block of [ruleId].
 *
 * `UiDiagnostic` carries no file, so a diagnostic reported against another file of the entry can
 * still land inside these line numbers — narrowing to one rule is an improvement on attributing the
 * whole buffer to it, not a guarantee. A diagnostic with no line at all is kept: nothing about it
 * says it belongs elsewhere, and dropping it would hide an error rather than move it.
 */
private fun diagnosticsForRule(
    diagnostics: List<UiDiagnostic>,
    ruleText: String,
    ruleId: String?,
): List<UiDiagnostic> {
    val lines = ruleId?.let { id -> ruleLineRange(fullText = ruleText, ruleId = id) } ?: return diagnostics
    return diagnostics.filter { diagnostic -> diagnostic.line == null || diagnostic.line in lines }
}
