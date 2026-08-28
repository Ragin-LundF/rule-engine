package ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.BorderColor
import ui.PrimaryBlue

/**
 * The grip that resizes a panel, in either direction.
 *
 * One implementation for both, because the two cases differ only in which axis the track fills and
 * which way the mark is turned. It was written for the right panel and is now also the dock's, and a
 * second copy would be the place the two drifted apart.
 *
 * The visible mark is a short centred bar rather than the full length: a full-length line reads as a
 * border — another edge of the panel — while a handle has to read as a thing you take hold of. The hit
 * area is the whole [THICKNESS] track even though the mark is 3dp, because a 3dp target is one a
 * pointer misses. The cursor changes on hover, which is the part that actually tells anyone the panel
 * can be resized at all.
 *
 * [PointerIcon.Hand] rather than a resize cursor: `commonMain` has no resize icon, and an awt-backed
 * one would move this file to the platform source set for a cosmetic gain.
 *
 * **The delta is negated in both orientations**, and the reason is one invariant rather than two
 * special cases: the handle sits on its panel's *leading* edge, so the panel grows in the opposite
 * direction to the drag. Dragging left widens the right panel; dragging up heightens the bottom dock.
 *
 * A note on cost, so the right panel's happy accident is not assumed to generalise: horizontally the
 * grip occupies a 12dp gap that was already in the layout, so resizability is free. Vertically there
 * was no gap, so the dock's grip takes [THICKNESS] away from the canvas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun PanelSplitter(
    orientation: Orientation,
    onDrag: (Dp) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Double-click to go back to the default size. Null leaves the gesture unbound.
     *
     * The only way out of a size dragged to an extreme on a display that is no longer attached — the
     * stored value is clamped, so it stays reachable, but "reachable" is not the same as "convenient".
     */
    onReset: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val density = LocalDensity.current
    val horizontal = orientation == Orientation.Horizontal

    val track = if (horizontal) {
        Modifier.fillMaxHeight().width(width = THICKNESS)
    } else {
        Modifier.fillMaxWidth().height(height = THICKNESS)
    }
    val mark = if (horizontal) {
        Modifier.width(width = MARK_THIN).height(height = MARK_LONG)
    } else {
        Modifier.width(width = MARK_LONG).height(height = MARK_THIN)
    }

    Box(
        modifier = modifier
            .then(other = track)
            .pointerHoverIcon(icon = PointerIcon.Hand)
            .hoverable(interactionSource = interaction)
            .draggable(
                orientation = orientation,
                state = rememberDraggableState { delta ->
                    onDrag(with(density) { -delta.toDp() })
                },
                onDragStarted = { onDraggingChange(true) },
                onDragStopped = { onDraggingChange(false) },
            )
            .then(
                other = if (onReset == null) {
                    Modifier
                } else {
                    // No ripple and no single-click action: the grip is a drag target, and a click that
                    // did something would fire on every aborted drag.
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onDoubleClick = onReset,
                        onClick = {},
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = mark
                .clip(shape = RoundedCornerShape(size = 2.dp))
                .background(color = if (hovered) PrimaryBlue else BorderColor),
        )
    }
}

/** The draggable track's thickness. Also what a caller must reserve for the grip. */
internal val THICKNESS: Dp = 12.dp

private val MARK_LONG: Dp = 36.dp
private val MARK_THIN: Dp = 3.dp
