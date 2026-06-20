package ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary

/**
 * Right panel with two tabs: Inspector and Simulate.
 *
 * @param tab               Currently selected tab.
 * @param onTabChange       Called when the user selects a different tab.
 * @param inspectorContent  Content shown in the Inspector tab.
 * @param simulateContent   Content shown in the Simulate tab.
 */
@Suppress("FunctionNaming")
@Composable
fun RightPanelWithTabs(
    tab: RightPanelTab,
    onTabChange: (RightPanelTab) -> Unit,
    inspectorContent: @Composable () -> Unit,
    simulateContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tab.ordinal

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height = 40.dp)
                .clip(shape = RoundedCornerShape(size = 10.dp))
                .background(color = BgElevated)
                .border(
                    width = 1.dp,
                    color = BorderColor,
                    shape = RoundedCornerShape(size = 10.dp),
                )
                .padding(all = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RightPanelTab.entries.forEachIndexed { index, tabEntry ->
                RightTab(
                    label = tabEntry.displayName(),
                    selected = selectedIndex == index,
                    onClick = { onTabChange(tabEntry) },
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(weight = 1f),
                )
            }
        }

        Box(modifier = Modifier.weight(weight = 1f)) {
            when (tab) {
                RightPanelTab.INSPECTOR -> inspectorContent()
                RightPanelTab.SIMULATE -> simulateContent()
            }
        }
    }
}

@Composable
private fun RightTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) PrimaryBlue else Color.Transparent
    val textColor = if (selected) TextPrimary else TextSecondary

    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = textColor,
        )
    }
}

private fun RightPanelTab.displayName(): String = when (this) {
    RightPanelTab.INSPECTOR -> "Inspector"
    RightPanelTab.SIMULATE -> "Simulate"
}
