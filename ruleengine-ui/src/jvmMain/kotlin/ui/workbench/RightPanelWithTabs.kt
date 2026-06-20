package ui.workbench

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted

private val TABS = RightPanelTab.entries.map { it.displayName() }

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
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = BgSurface,
            contentColor = PrimaryBlue,
            edgePadding = 0.dp,
            divider = { TabRowDefaults.Divider(color = BorderColor, thickness = 1.dp) },
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = PrimaryBlue,
                    height = 2.dp,
                )
            },
        ) {
            RightPanelTab.entries.forEachIndexed { index, tabEntry ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onTabChange(tabEntry) },
                    text = {
                        Text(
                            text = tabEntry.displayName(),
                            color = if (selectedIndex == index) PrimaryBlue else TextMuted,
                        )
                    },
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

private fun RightPanelTab.displayName(): String = when (this) {
    RightPanelTab.INSPECTOR -> "Inspector"
    RightPanelTab.SIMULATE -> "Simulate"
}
