package ui.workbench.export

import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.saveBytesToFile
import java.time.LocalDate

/**
 * Exports the whole manifest entry as a rule overview, for a wiki or for a customer.
 *
 * A function rather than the button it used to be: the area header takes its actions as data, one entry
 * per format, so the two formats are two rows of the `⋯` menu instead of a button with a menu of its
 * own hanging off the bar. The export itself is unchanged.
 */
internal fun exportRuleOverview(state: RuleEditorState, format: RuleOverviewExport.Format) {
    runCatching {
        when (val result = RuleOverviewExport.export(state = state, format = format, today = LocalDate.now())) {
            is RuleOverviewExport.Result.Unavailable ->
                state.setStatus(msg = result.reason, kind = StatusKind.IDLE)

            is RuleOverviewExport.Result.Ready -> {
                val written = saveBytesToFile(
                    title = "Export rule overview",
                    suggestedName = result.fileName,
                    bytes = result.bytes,
                )
                if (written == null) {
                    state.setStatus(msg = "Export cancelled", kind = StatusKind.IDLE)
                } else {
                    state.setStatus(
                        msg = exportMessage(state = state, written = written, result = result),
                        kind = StatusKind.SUCCESS,
                    )
                }
            }
        }
    }.onFailure { cause ->
        state.setStatus(msg = "Export failed: ${cause.message}", kind = StatusKind.ERROR)
    }
}

/**
 * The export reads the rule files from disk, so an unsaved edit in the open file is not in the
 * document. Saying so beats letting the author hand over a version they think contains their change.
 */
private fun exportMessage(
    state: RuleEditorState,
    written: String,
    result: RuleOverviewExport.Result.Ready,
): String {
    val exported = "Exported ${result.ruleCount} rule(s) to $written"

    return if (state.currentRuleFileHasUnsavedChanges()) {
        "$exported — the open rule file has unsaved changes, which are not included"
    } else {
        exported
    }
}
