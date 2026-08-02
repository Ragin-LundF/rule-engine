package ui.components

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

/**
 * Tab switcher for an editor area's center-panel modes.
 *
 * Shared by the Manifest, Field Schema and Action Schema areas: three strips that differ only in the
 * enum they switch over, and that had already drifted apart in corner radius, text style and label
 * casing while being nominally the same control.
 *
 * [label] rather than a `toString` on the mode, because the display names are the area's to choose:
 * deriving them from the enum constant is what produced "Yaml" next to the other areas' "YAML".
 */
@Composable
fun <T> ModeTabs(
    modes: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
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
        modes.forEach { mode ->
            val selected = mode == current
            Text(
                text = label(mode),
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
