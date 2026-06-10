package ui.editor.rules.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ruleengine.manifest.ManifestLoader
import ui.BgElevated
import ui.BgSurface
import ui.PrimaryBlue
import ui.TextPrimary
import ui.editor.rules.AppButton
import ui.editor.rules.Chip
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.editor.rules.drawBottomLine
import ui.pickManifestFile

/** Top bar section: title and Load Manifest button. */
@Composable
fun TopBarSection(state: RuleEditorState, scope: CoroutineScope) {
    Surface(color = BgSurface, elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBottomLine(w = 1.dp, color = ui.BorderColor)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            Text(text = "⚙", fontSize = 18.sp)
            Text(text = "Rule Engine", style = MaterialTheme.typography.h6, color = TextPrimary)
            Chip(label = "Editor", bg = BgElevated, textColor = PrimaryBlue)
            Spacer(Modifier.weight(weight = 1f))
            AppButton(label = "Load Manifest") {
                scope.launch {
                    val m = pickManifestFile()
                    if (m != null) {
                        state.manifestText.value = m.first
                        state.manifestBaseDir.value = m.second
                        // Reset selection so auto-load of first entry triggers.
                        state.selectedManifestEntry.value = null
                        state.parsedManifest.value = runCatching {
                            ManifestLoader.loadFromString(content = state.manifestText.value)
                        }.getOrNull()
                        state.setStatus(msg = "Manifest loaded", kind = StatusKind.SUCCESS)
                    } else {
                        state.setStatus(msg = "Manifest load cancelled", kind = StatusKind.IDLE)
                    }
                }
            }
        }
    }
}

