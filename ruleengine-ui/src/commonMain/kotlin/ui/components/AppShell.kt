package ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.Bg
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
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
    /**
     * Called with each drag delta, in dp, when the right panel is resizable.
     *
     * Null means it is not — a collapsed panel has one correct width, and offering a handle that
     * silently refuses to move is worse than offering none.
     */
    onRightPanelResize: ((Dp) -> Unit)? = null,
) {
    // Animated only when something else changes the width — opening, closing, a restored preference.
    // Animating a drag makes the panel lag a frame behind the pointer, which feels like the handle has
    // come loose from the edge it is moving.
    val animatedRightPanelWidth by animateDpAsState(targetValue = rightPanelWidth)
    var dragging by remember { mutableStateOf(value = false) }
    val effectiveWidth = if (dragging) rightPanelWidth else animatedRightPanelWidth

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
            if (onRightPanelResize == null) {
                Box(modifier = Modifier.width(width = 12.dp))
            } else {
                PanelSplitter(
                    onDrag = onRightPanelResize,
                    onDraggingChange = { active -> dragging = active },
                )
            }
            PanelContainer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width = effectiveWidth),
            ) {
                rightPanel()
            }
        }
        bottomBar()
    }
}

/**
 * The grip between the centre panel and the right panel.
 *
 * It occupies the 12dp gap that was already there, so making the panel resizable costs no width. The
 * visible mark is a short centred bar rather than the full height: a full-height line reads as a border
 * — another edge of the panel — while a handle has to read as a thing you take hold of.
 *
 * The hit area is the whole 12dp column even though the mark is 3dp wide, because a 3dp target is one a
 * pointer misses. The cursor changes on hover, which is the part that actually tells anyone the panel
 * can be resized at all.
 */
@Composable
private fun PanelSplitter(onDrag: (Dp) -> Unit, onDraggingChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(width = 12.dp)
            .pointerHoverIcon(icon = PointerIcon.Hand)
            .hoverable(interactionSource = interaction)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // Dragging left widens the panel, so the delta is negated: the handle is on the
                    // panel's leading edge, and the width grows in the opposite direction to the move.
                    onDrag(with(density) { -delta.toDp() })
                },
                onDragStarted = { onDraggingChange(true) },
                onDragStopped = { onDraggingChange(false) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(width = 3.dp)
                .height(height = 36.dp)
                .clip(shape = RoundedCornerShape(size = 2.dp))
                .background(color = if (hovered) PrimaryBlue else BorderColor),
        )
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
