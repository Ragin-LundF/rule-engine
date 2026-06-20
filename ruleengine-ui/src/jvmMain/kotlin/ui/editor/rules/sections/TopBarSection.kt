package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ruleengine.manifest.ManifestLoader
import java.nio.file.Path
import ui.BgSurface
import ui.PrimaryBlue
import ui.TextPrimary
import ui.components.PrimaryButton
import ui.components.StatusBadge
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.pickManifestFile

/** Top bar section: app brand, mode badge and Load Manifest action. */
@Composable
fun TopBarSection(
    state: RuleEditorState,
    scope: CoroutineScope,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = BgSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        AppLogo()
        Text(
            text = "Rule Engine",
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        StatusBadge(
            label = "WORKBENCH",
            color = PrimaryBlue,
        )
        Spacer(modifier = Modifier.weight(weight = 1f))
        PrimaryButton(
            text = "Load Manifest",
            onClick = {
                scope.launch {
                    val m = pickManifestFile()
                    if (m != null) {
                        state.manifestText.value = m.first
                        state.manifestBaseDir.value = Path.of(m.second).toAbsolutePath().normalize().toString()
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
            },
        )
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Text(
        text = "◆",
        fontSize = 22.sp,
        color = PrimaryBlue,
        modifier = modifier
            .clip(shape = CircleShape)
            .background(color = PrimaryBlue.copy(alpha = 0.12f))
            .padding(8.dp),
    )
}
