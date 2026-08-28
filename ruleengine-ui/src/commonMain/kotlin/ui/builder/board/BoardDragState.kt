package ui.builder.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import ui.builder.board.model.DropTarget

/**
 * What is being dragged on the board, and where it could go.
 *
 * Held in plain Compose `MutableState` rather than a flow, for the same reason
 * `BuilderRulesController` is: a drag has to move in the frame the pointer moved, and a flow adds a
 * dispatch hop. This is also the first `pointerInput` gesture in the module, so it is written to be
 * readable rather than clever — one state object, one registry, no gesture library.
 *
 * The registry is the part worth explaining. A drop target's position is only known after layout, so
 * each target reports its own bounds via `Modifier.onGloballyPositioned` and this object stores them by
 * key. Hit-testing then happens against stored rectangles rather than against the composition tree,
 * which is what lets a card know it is over a lane it has no parent-child relationship with.
 *
 * Targets re-register on every layout pass, so a stale rectangle survives at most one frame — but a
 * target that is removed never re-registers, so [clear] drops the whole registry at the end of a drag
 * rather than trusting it between gestures.
 */
class BoardDragState {

    /** What is being dragged, or null when nothing is. */
    var dragged: DragSubject? by mutableStateOf(value = null)
        private set

    /** Where the pointer is, in root coordinates. Null when no drag is active. */
    var pointer: Offset? by mutableStateOf(value = null)
        private set

    /** The target under the pointer, recomputed as it moves. */
    var hovered: DropTarget? by mutableStateOf(value = null)
        private set

    /** Why the current hover would be refused, or null when it would be accepted. */
    var refusal: String? by mutableStateOf(value = null)
        private set

    private val targets: MutableMap<DropTarget, Rect> = mutableMapOf()

    /** What a drag is carrying: a condition row, or a statement out of one branch. */
    sealed interface DragSubject {
        data class Row(val nodeId: String) : DragSubject
        data class Statement(val statementId: String, val from: DropTarget.Lane) : DragSubject
    }

    /**
     * Records where [target] currently is.
     *
     * Called from `onGloballyPositioned`, so it runs on every layout pass and must stay cheap — hence a
     * map write and nothing else. No recomposition is triggered by it: the registry is read during a
     * drag, and the drag's own state is what recomposes.
     */
    fun register(target: DropTarget, bounds: Rect) {
        targets[target] = bounds
    }

    /** Forgets [target], for a lane or group that has left the composition. */
    fun unregister(target: DropTarget) {
        targets.remove(key = target)
    }

    fun start(subject: DragSubject, at: Offset) {
        dragged = subject
        pointer = at
        hovered = null
        refusal = null
    }

    /**
     * Moves the pointer and recomputes the hover.
     *
     * [validate] is passed in rather than encoded here because whether a drop is legal is a question
     * about the rule — whether it would empty a `then` block, whether a row can join a group — and that
     * belongs to `BuilderEditorState`, not to a gesture.
     */
    fun moveTo(at: Offset, validate: (DragSubject, DropTarget) -> String?) {
        val subject = dragged ?: return
        pointer = at

        val target = targets.entries
            .firstOrNull { (_, bounds) -> bounds.contains(offset = at) }
            ?.key

        hovered = target
        refusal = target?.let { candidate -> validate(subject, candidate) }
    }

    /**
     * Ends the drag and returns the target to drop on, or null.
     *
     * Returns null for a refused drop as well as for no target, so a caller cannot accidentally apply
     * one: the reason is in [refusal] and the caller reads it before this clears.
     */
    fun finish(): DropTarget? {
        val target = hovered?.takeIf { refusal == null }
        clear()
        return target
    }

    /** Abandons the drag. The registry goes too — see the note on the class. */
    fun clear() {
        dragged = null
        pointer = null
        hovered = null
        refusal = null
        targets.clear()
    }
}
