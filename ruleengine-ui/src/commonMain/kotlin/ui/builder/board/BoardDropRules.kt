package ui.builder.board

import ruleengine.core.domain.dto.RuleBranch
import ui.builder.board.model.DropTarget
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.groupIdContaining
import ui.builder.model.mutable.moveConditionInto
import ui.builder.model.mutable.moveStatement

/**
 * Whether a drop is legal, and what it does.
 *
 * Separated from the gesture and from the rendering because these are statements about the *rule*, not
 * about the pointer: dropping a row on itself changes nothing, and moving the last `then` action out of
 * `then` produces a rule the DSL cannot express. Both must be refused identically however the gesture
 * arrives — by drag today, by keyboard later — and a refusal has to carry a reason, because a drag that
 * silently springs back is indistinguishable from a broken drag.
 */

/** The reason [target] would refuse [subject], or null when it would accept. */
internal fun validateDrop(
    state: BuilderEditorState,
    subject: BoardDragState.DragSubject,
    target: DropTarget,
): String? = when (subject) {
    is BoardDragState.DragSubject.Row -> validateRowDrop(
        state = state,
        nodeId = subject.nodeId,
        target = target,
    )

    is BoardDragState.DragSubject.Statement -> validateStatementDrop(
        state = state,
        subject = subject,
        target = target,
    )
}

private fun validateRowDrop(
    state: BuilderEditorState,
    nodeId: String,
    target: DropTarget,
): String? = when (target) {
    is DropTarget.Row -> when {
        target.nodeId == nodeId -> "A row cannot be grouped with itself."
        else -> null
    }

    is DropTarget.Group -> when {
        // Dropping a group into itself, or into one of its own descendants, would detach the subtree
        // from the rule. The container walk is what makes this checkable without a parent pointer.
        target.groupId == nodeId -> "A group cannot be dropped into itself."
        isInside(state = state, ancestorId = nodeId, candidateId = target.groupId) ->
            "A group cannot be dropped inside itself."

        else -> null
    }

    is DropTarget.Lane -> "A condition belongs in `when`, not in an outcome block."
}

private fun validateStatementDrop(
    state: BuilderEditorState,
    subject: BoardDragState.DragSubject.Statement,
    target: DropTarget,
): String? = when (target) {
    is DropTarget.Lane -> when {
        target.branch == subject.from.branch -> null
        else -> state.blockedMove(id = subject.statementId, from = subject.from.branch)
    }

    is DropTarget.Row, is DropTarget.Group ->
        "An outcome belongs in `then`, `else` or `not_exists`, not in a condition."
}

/** Applies a row drop that [validateDrop] has already accepted. */
internal fun applyRowDrop(state: BuilderEditorState, nodeId: String, target: DropTarget) {
    when (target) {
        // Two rows become a group of two — this is how a group is created by dragging, and it reuses
        // exactly the grouping the outline's keyboard path uses.
        is DropTarget.Row -> state.groupConditions(ids = setOf(nodeId, target.nodeId))
        is DropTarget.Group -> state.moveConditionInto(id = nodeId, groupId = target.groupId)
        is DropTarget.Lane -> Unit
    }
}

/** Applies a statement drop that [validateDrop] has already accepted. */
internal fun applyStatementDrop(
    state: BuilderEditorState,
    statementId: String,
    from: RuleBranch,
    target: DropTarget,
) {
    when (target) {
        is DropTarget.Lane -> state.moveStatement(
            id = statementId,
            from = from,
            to = target.branch,
        )

        is DropTarget.Row, is DropTarget.Group -> Unit
    }
}

/** True when [candidateId] sits anywhere beneath [ancestorId]. */
private fun isInside(state: BuilderEditorState, ancestorId: String, candidateId: String): Boolean {
    var container = state.groupIdContaining(id = candidateId)
    while (container != null) {
        if (container == ancestorId) {
            return true
        }
        container = state.groupIdContaining(id = container)
    }
    return false
}
