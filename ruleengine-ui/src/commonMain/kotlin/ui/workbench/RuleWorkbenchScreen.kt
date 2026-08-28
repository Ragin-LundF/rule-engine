package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.WorkbenchShell

/**
 * Shared workbench screen shell.
 * Wires the [WorkbenchShell] layout with platform-supplied composable slots.
 * All business logic and platform-specific operations live in the caller.
 */
@Composable
fun RuleWorkbenchScreen(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    iconRail: @Composable () -> Unit = {},
    centerContent: @Composable () -> Unit,
    rightPanel: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    rightPanelWidth: Dp = 320.dp,
    /** Forwarded to the shell's splitter; null leaves the panel a fixed width. */
    onRightPanelResize: ((Dp) -> Unit)? = null,
) {
    WorkbenchShell(
        topBar = topBar,
        bottomBar = bottomBar,
        iconRail = iconRail,
        centerContent = centerContent,
        rightPanel = rightPanel,
        modifier = modifier,
        rightPanelWidth = rightPanelWidth,
        onRightPanelResize = onRightPanelResize,
    )
}
