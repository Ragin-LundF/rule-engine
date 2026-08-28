package ui.builder.board

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import ui.builder.board.model.DropTarget

/**
 * The two halves of a board drag: the thing you pick up, and the place it can land.
 *
 * They live in one file because they are one mechanism — neither is usable without the other, and the
 * coordinate space they agree on (root, not local) is the contract between them. Splitting them would
 * put that contract in two files and invite one side to change it.
 *
 * There is no drag gesture anywhere else in this module, so a note on why it is built this way:
 * `detectDragGestures` reports offsets in the *local* space of the node that consumed the gesture,
 * while a drop target's bounds are only meaningful in root space. So [draggable] tracks its own root
 * origin from layout and converts, and [dropTarget] registers `boundsInRoot`. Every position that
 * crosses between the two is therefore in the same space, which is the bug this shape exists to avoid.
 */
internal fun Modifier.draggable(
    state: BoardDragState,
    subject: BoardDragState.DragSubject,
    validate: (BoardDragState.DragSubject, DropTarget) -> String?,
    onDrop: (DropTarget) -> Unit,
    onRefused: (String) -> Unit,
): Modifier = withRootOrigin { origin ->
    pointerInput(subject) {
        detectDragGestures(
            onDragStart = { local -> state.start(subject = subject, at = origin() + local) },
            onDrag = { change, _ ->
                state.moveTo(at = origin() + change.position, validate = validate)
            },
            onDragEnd = {
                // Read the refusal before finishing: finish() clears it, and the reason is the whole
                // value of a refused drop — a drag that just springs back teaches nothing.
                val refusal = state.refusal
                val target = state.finish()
                when {
                    target != null -> onDrop(target)
                    refusal != null -> onRefused(refusal)
                }
            },
            onDragCancel = { state.clear() },
        )
    }
}

/**
 * Registers this node as [target] for the duration of its life in the composition.
 *
 * Re-registers on every layout pass, which is what keeps the rectangle correct while the board scrolls
 * under a drag.
 */
internal fun Modifier.dropTarget(state: BoardDragState, target: DropTarget): Modifier {
    return onGloballyPositioned { coordinates ->
        state.register(target = target, bounds = coordinates.boundsInRoot())
    }
}

/**
 * Tracks this node's root origin and hands it to [block] as a getter.
 *
 * A getter rather than a value because layout and the gesture run at different times: reading the
 * origin when the drag *moves* rather than when the modifier is built is what makes a drag survive the
 * board scrolling beneath it.
 */
private fun Modifier.withRootOrigin(block: Modifier.(origin: () -> Offset) -> Modifier): Modifier {
    var origin = Offset.Zero
    return this
        .onGloballyPositioned { coordinates -> origin = coordinates.boundsInRoot().topLeft }
        .block { origin }
}
