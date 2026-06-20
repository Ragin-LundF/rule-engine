package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary
import ui.workbench.RuleMode

/**
 * Horizontal tab bar for switching between rule-center modes.
 * Renders one tab per [RuleMode] value.
 */
@Composable
fun WorkbenchTabs(
    selectedMode: RuleMode,
    onModeSelected: (RuleMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleMode.entries.forEach { mode ->
            WorkbenchTab(
                label = mode.displayName(),
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun WorkbenchTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) BgElevated else BgSurface
    val textColor = if (selected) PrimaryBlue else TextSecondary

    Box(
        modifier = Modifier
            .clip(shape = MaterialTheme.shapes.small)
            .background(color = bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            color = textColor,
        )
    }
}

private fun RuleMode.displayName(): String = when (this) {
    RuleMode.BUILDER -> "Builder"
    RuleMode.CODE -> "Code"
    RuleMode.DIAGRAM -> "Diagram"
    RuleMode.TEST -> "Test"
    RuleMode.TABLE -> "Table"
}
