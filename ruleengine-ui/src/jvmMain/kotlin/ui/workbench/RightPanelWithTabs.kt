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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextOnPrimary
import ui.TextSecondary
import ui.components.TinyButton
import ui.components.rotateVertically
import ui.workbench.model.RightPanelTab

/**
 * Right panel with two tabs: Inspector and Simulate.
 *
 * Collapsible to a narrow strip: only one switching icon is ever visible — the collapse
 * icon (`⟩`) at the end of the tab row when expanded, or the expand icon (`⟨`) on the
 * strip itself when collapsed.
 *
 * @param tab               Currently selected tab.
 * @param onTabChange       Called when the user selects a different tab.
 * @param inspectorContent  Content shown in the Inspector tab.
 * @param simulateContent   Content shown in the Simulate tab.
 * @param expanded          Whether the panel shows its full tabbed layout or a collapsed strip.
 * @param onToggleExpanded  Called when the user clicks the switching icon (in either state).
 */
@Suppress("FunctionNaming")
@Composable
fun RightPanelWithTabs(
    tab: RightPanelTab,
    onTabChange: (RightPanelTab) -> Unit,
    inspectorContent: @Composable () -> Unit,
    simulateContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
) {
    if (!expanded) {
        CollapsedRightPanelStrip(onToggleExpanded = onToggleExpanded, modifier = modifier)
        return
    }

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
            TinyButton(
                text = "⟩",
                onClick = onToggleExpanded,
                modifier = Modifier.size(size = 28.dp),
            )
        }

        Box(modifier = Modifier.weight(weight = 1f)) {
            when (tab) {
                RightPanelTab.INSPECTOR -> inspectorContent()
                RightPanelTab.SIMULATE -> simulateContent()
            }
        }
    }
}

/** Collapsed right-panel strip: the whole column is clickable and re-expands the panel. */
@Suppress("FunctionNaming")
@Composable
private fun CollapsedRightPanelStrip(
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onToggleExpanded),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TinyButton(
            text = "⟨",
            onClick = onToggleExpanded,
            modifier = Modifier.padding(top = 6.dp).size(size = 28.dp),
        )
        Text(
            text = "INSPECTOR · SIMULATE",
            style = MaterialTheme.typography.caption.copy(letterSpacing = 1.5.sp),
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(top = 16.dp)
                .rotateVertically(),
        )
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
    // TextOnPrimary, not TextPrimary: the selected tab sits on PrimaryBlue, and TextPrimary is
    // near-black in the light theme.
    val textColor = if (selected) TextOnPrimary else TextSecondary

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
