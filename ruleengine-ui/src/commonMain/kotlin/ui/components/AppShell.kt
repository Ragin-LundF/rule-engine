package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.Bg
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary

/**
 * Top toolbar with app title on the left and action slots on the right.
 */
@Composable
fun TopToolbar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h6,
            color = PrimaryBlue,
        )
        Spacer(modifier = Modifier.weight(weight = 1f))
        actions()
    }
}

/**
 * Narrow vertical icon rail on the left edge of the workbench.
 * Content should be icon buttons stacked vertically.
 */
@Composable
fun IconRail(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * Full workbench shell layout:
 * TopToolbar / (IconRail | centerContent | rightPanel) / BottomStatusBar
 *
 * All panel slots are optional — pass empty composables for unused slots.
 */
@Composable
fun WorkbenchShell(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    iconRail: @Composable () -> Unit,
    centerContent: @Composable () -> Unit,
    rightPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = Bg),
    ) {
        topBar()
        Row(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxWidth(),
        ) {
            iconRail()
            Box(modifier = Modifier.weight(weight = 1f).fillMaxHeight()) { centerContent() }
            Box(modifier = Modifier.weight(weight = 0.28f).fillMaxHeight()) { rightPanel() }
        }
        bottomBar()
    }
}

/**
 * A placeholder box used during incremental layout development.
 * Shows a labeled bordered area so the layout structure is visible.
 */
@Composable
fun PlaceholderPanel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(width = 1.dp, color = BorderColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
