package ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Lays the element out rotated 90° clockwise, e.g. for a label in a narrow vertical strip.
 *
 * Plain [rotate] only rotates the pixels — the element is still measured with the strip's narrow
 * width, so text wraps letter-by-letter before rotating. This modifier measures unconstrained and
 * swaps the reported width/height, so a single-line text renders as one clean vertical line.
 */
fun Modifier.rotateVertically(): Modifier = this
    .layout { measurable, _ ->
        val placeable = measurable.measure(constraints = Constraints())
        layout(width = placeable.height, height = placeable.width) {
            placeable.place(
                x = -(placeable.width - placeable.height) / 2,
                y = -(placeable.height - placeable.width) / 2,
            )
        }
    }
    .rotate(degrees = 90f)
