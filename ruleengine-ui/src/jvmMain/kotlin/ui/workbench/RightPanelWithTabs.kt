package ui.workbench

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.BorderColor
import ui.PrimaryBlue
import ui.BgSurface
import ui.TextMuted

private val TABS = listOf("Inspector", "Simulate")

/**
 * Right panel with two tabs: Inspector and Simulate.
 *
 * @param tab               Currently selected tab index (0 = Inspector, 1 = Simulate).
 * @param onTabChange       Called when the user selects a different tab.
 * @param inspectorContent  Content shown in the Inspector tab.
 * @param simulateContent   Content shown in the Simulate tab.
 */
@Suppress("FunctionNaming")
@Composable
fun RightPanelWithTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    inspectorContent: @Composable () -> Unit,
    simulateContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = BgSurface,
            contentColor = PrimaryBlue,
            edgePadding = 0.dp,
            divider = { TabRowDefaults.Divider(color = BorderColor, thickness = 1.dp) },
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tab]),
                    color = PrimaryBlue,
                    height = 2.dp,
                )
            },
        ) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { onTabChange(index) },
                    text = {
                        Text(
                            text = title,
                            color = if (tab == index) PrimaryBlue else TextMuted,
                        )
                    },
                )
            }
        }

        when (tab) {
            0 -> inspectorContent()
            1 -> simulateContent()
        }
    }
}
