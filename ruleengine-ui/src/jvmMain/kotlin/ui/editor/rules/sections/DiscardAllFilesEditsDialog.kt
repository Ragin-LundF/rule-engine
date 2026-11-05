package ui.editor.rules.sections

import androidx.compose.runtime.Composable
import ui.components.ConfirmDialog
import ui.editor.rules.RuleEditorState

/**
 * Asks before leaving an edited All-files view for one of its files.
 *
 * The joined buffer cannot be written back to the files it came from, so the edit would be lost; the
 * user either discards it or stays in All files to copy it out or undo it.
 */
@Composable
fun DiscardAllFilesEditsDialog(state: RuleEditorState) {
    val pending = state.pendingRuleFileSwitch.value ?: return
    ConfirmDialog(
        title = "Discard edits in All files?",
        message = "The All files view has edits that cannot be saved back to the individual files. " +
                "Opening $pending discards them.",
        confirmLabel = "Discard and open",
        onConfirm = state::discardAllFilesEditsAndOpenPending,
        dismissLabel = "Stay in All files",
        onDismiss = { state.pendingRuleFileSwitch.value = null },
    )
}
