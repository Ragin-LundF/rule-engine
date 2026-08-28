package ui.dock

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.PanelSplitter
import ui.components.THICKNESS

/**
 * A canvas with a resizable reference dock beneath it.
 *
 * Lives inside the centre panel rather than in the shell's `bottomBar`, and that placement is the
 * design. `BoxWithConstraints` here gives the dock's clamp the available height *exactly*; in the shell
 * it would have to be inferred by subtracting the unknown intrinsic heights of the top bar, the
 * diagnostics section and the status bar, and a dock that got it wrong would push the status bar off
 * the bottom of the window. A shell-level dock would also span under the icon rail and the Inspector,
 * when what it previews is the centre panel's own file.
 *
 * **Two clamps, and only one is remembered.** [dockHeight] is what the reader asked for and is what
 * gets persisted. What is rendered is that capped by whatever is left once the canvas keeps
 * [MIN_CANVAS_HEIGHT] — so shrinking the window borrows height from the dock and growing it again gives
 * the height back, rather than a small window quietly overwriting a preference set on a large one.
 * The cap travels out through [onDockResize] as `available`, so a drag cannot run past a limit the
 * layout is already enforcing and leave the grip stranded away from the edge it is moving.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun CanvasDockScaffold(
    expanded: Boolean,
    dockHeight: Dp,
    modifier: Modifier = Modifier,
    /**
     * Called with the drag delta and the tallest the dock may currently be.
     *
     * Null means the dock is not resizable — which is the case while it is collapsed, because a grip
     * that silently refuses to move is worse than no grip at all.
     */
    onDockResize: ((delta: Dp, available: Dp) -> Unit)? = null,
    /** Double-clicking the grip goes back to the default height. */
    onDockResetHeight: (() -> Unit)? = null,
    dock: @Composable () -> Unit,
    canvas: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val ceiling = (maxHeight - MIN_CANVAS_HEIGHT - COLLAPSED_DOCK_HEIGHT - THICKNESS)
            .coerceAtLeast(minimumValue = 0.dp)
        val requested = dockHeight.coerceAtMost(maximumValue = ceiling)

        // Animated except while dragging, exactly as the right panel is: animating a drag leaves the
        // panel a frame behind the pointer, which feels like the grip has come loose from the edge.
        var dragging by remember { mutableStateOf(value = false) }
        val animated by animateDpAsState(targetValue = requested)
        val effective = if (dragging) requested else animated

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(weight = 1f)) {
                canvas()
            }
            if (expanded && onDockResize != null) {
                PanelSplitter(
                    orientation = Orientation.Vertical,
                    onDrag = { delta -> onDockResize(delta, ceiling) },
                    onDraggingChange = { active -> dragging = active },
                    onReset = onDockResetHeight,
                )
            }
            Box(
                modifier = if (expanded) {
                    Modifier.fillMaxWidth().height(height = effective)
                } else {
                    // Collapsed: the dock is its own header and nothing more, so it takes the height
                    // it needs rather than a height someone chose.
                    Modifier.fillMaxWidth()
                },
            ) {
                dock()
            }
        }
    }
}

/**
 * The least the canvas may be squeezed to.
 *
 * Mirrors `BoardCanvas`'s own floor, which is private to that package. Below this the canvas stops
 * being the work and the dock stops being reference material.
 */
internal val MIN_CANVAS_HEIGHT: Dp = 170.dp
