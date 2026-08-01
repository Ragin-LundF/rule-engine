package ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.Bg
import ui.BgSurface
import ui.TextSecondary

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
            .width(width = 76.dp)
            .padding(vertical = 12.dp),
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
 * The shell wraps each side panel in a rounded surface card with a small gap
 * so the background colour creates clean visual separation.
 */
@Composable
fun WorkbenchShell(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    iconRail: @Composable () -> Unit,
    centerContent: @Composable () -> Unit,
    rightPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    rightPanelWidth: Dp = 320.dp,
) {
    val animatedRightPanelWidth by animateDpAsState(targetValue = rightPanelWidth)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = Bg),
    ) {
        topBar()
        Row(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelContainer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width = 92.dp),
            ) {
                IconRail(content = iconRail)
            }
            Box(modifier = Modifier.width(width = 12.dp))
            PanelContainer(
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxHeight(),
            ) {
                centerContent()
            }
            Box(modifier = Modifier.width(width = 12.dp))
            PanelContainer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width = animatedRightPanelWidth),
            ) {
                rightPanel()
            }
        }
        bottomBar()
    }
}

@Composable
private fun PanelContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .padding(all = 14.dp),
    ) {
        content()
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
            .clip(shape = MaterialTheme.shapes.large)
            .background(color = Bg)
            .padding(all = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
