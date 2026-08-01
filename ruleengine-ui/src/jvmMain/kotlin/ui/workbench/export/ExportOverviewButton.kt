package ui.workbench.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ui.BgElevated
import ui.BorderColor
import ui.TextPrimary
import ui.components.ToolbarButton
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.saveBytesToFile
import java.time.LocalDate

/**
 * Exports the whole manifest entry as a rule overview, for a wiki or for a customer.
 *
 * Disabled rather than hidden when no entry is selected: the action exists for every project, and a
 * button that disappears reads as a feature that is missing rather than as one not yet applicable.
 */
@Suppress("FunctionNaming")
@Composable
fun ExportOverviewButton(state: RuleEditorState, scope: CoroutineScope) {
    var expanded by remember { mutableStateOf(value = false) }
    val entrySelected by state.selectedManifestEntry

    Box {
        ToolbarButton(
            label = "Export Overview",
            enabled = entrySelected != null,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(color = BgElevated)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
        ) {
            RuleOverviewExport.Format.entries.forEach { format ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        scope.launch { runExport(state = state, format = format) }
                    },
                ) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.body2,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

private fun runExport(state: RuleEditorState, format: RuleOverviewExport.Format) {
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
