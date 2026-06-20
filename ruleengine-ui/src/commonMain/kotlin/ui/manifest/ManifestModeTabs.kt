package ui.manifest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.workbench.ManifestMode

@Composable
fun ManifestModeTabs(
    current: ManifestMode,
    onSelect: (ManifestMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ManifestMode.values().forEach { mode ->
            val isSelected = mode == current
            val bg = if (isSelected) PrimaryBlue else MaterialTheme.colors.surface
            val fg = if (isSelected) TextPrimary else TextSecondary
            Text(
                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                color = fg,
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(bg)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}
