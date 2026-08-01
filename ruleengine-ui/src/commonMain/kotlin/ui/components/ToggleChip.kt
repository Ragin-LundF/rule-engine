package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.BgElevated
import ui.BorderColor
import ui.TextSecondary

/**
 * A small toggle chip. In the selected state it is visibly highlighted
 * (green by default) so it is never mistaken for a disabled/grey item.
 */
@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = if (selected) AccentGreen else BgElevated
    val borderColor = if (selected) AccentGreen else BorderColor
    val textColor = if (selected) MaterialTheme.colors.onSecondary else TextSecondary

    Text(
        text = label,
        style = MaterialTheme.typography.body2.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = if (enabled) textColor else TextSecondary.copy(alpha = 0.5f),
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (enabled) bg else bg.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (enabled) borderColor else BorderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
