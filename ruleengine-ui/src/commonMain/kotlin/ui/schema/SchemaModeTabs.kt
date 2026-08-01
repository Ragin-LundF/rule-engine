package ui.schema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgHover
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.workbench.model.mode.SchemaMode

/**
 * Tab switcher for the Field Schema editor area: Visual / YAML / Usages.
 */
@Composable
fun SchemaModeTabs(
    current: SchemaMode,
    onSelect: (SchemaMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgElevated)
            .padding(all = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SchemaMode.entries.forEach { mode ->
            val selected = mode == current
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.caption,
                color = if (selected) TextPrimary else TextSecondary,
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(size = 4.dp))
                    .background(color = if (selected) PrimaryBlue else BgHover)
                    .clickable(onClick = { onSelect(mode) })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private val SchemaMode.displayName: String
    get() = when (this) {
        SchemaMode.VISUAL -> "Visual"
        SchemaMode.YAML -> "YAML"
        SchemaMode.USAGES -> "Usages"
    }
