package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgHover
import ui.PrimaryBlue
import ui.TextSecondary

/**
 * Narrow vertical rail that switches the active [AppArea].
 * Each area is represented by a short text label.
 */
@Composable
fun AppAreaIconRail(
    selectedArea: AppArea,
    onAreaSelected: (AppArea) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width = 76.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        AppArea.entries.forEach { area ->
            RailItem(
                area = area,
                selected = area == selectedArea,
                onClick = { onAreaSelected(area) },
            )
        }
    }
}

@Composable
private fun RailItem(
    area: AppArea,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = area.label()
    val symbol = area.symbol()
    val bg = if (selected) PrimaryBlue else BgElevated
    val textColor = if (selected) MaterialTheme.colors.onPrimary else TextSecondary
    val borderColor = if (selected) PrimaryBlue.copy(alpha = 0.7f) else BgHover

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 10.dp))
            .background(color = bg)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(size = 10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.subtitle1.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = textColor,
        )
        Spacer(modifier = Modifier.height(height = 2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = textColor,
        )
    }
}

private fun AppArea.symbol(): String = when (this) {
    AppArea.RULES -> "☰"
    AppArea.SCHEMA -> "⊞"
    AppArea.ACTIONS -> "◇"
    AppArea.MANIFEST -> "▣"
    AppArea.SAMPLES -> "▷"
    AppArea.SETTINGS -> "⚙"
}

private fun AppArea.label(): String = when (this) {
    AppArea.RULES -> "Rules"
    AppArea.SCHEMA -> "Schema"
    AppArea.ACTIONS -> "Actions"
    AppArea.MANIFEST -> "Manifest"
    AppArea.SAMPLES -> "Samples"
    AppArea.SETTINGS -> "Settings"
}
