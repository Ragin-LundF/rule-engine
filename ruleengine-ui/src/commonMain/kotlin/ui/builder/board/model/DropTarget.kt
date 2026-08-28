package ui.builder.board.model

import ruleengine.core.domain.dto.RuleBranch

/**
 * Somewhere a dragged thing can land.
 *
 * A sealed type rather than a string key so that a target and the thing dragged onto it are checked by
 * the compiler: a statement cannot be dropped on a condition group, and the `when` of the two cases is
 * exhaustive at every site that acts on a drop.
 */
sealed interface DropTarget {

    /** An existing group's interior — dropping a row here puts it inside the brackets. */
    data class Group(val groupId: String) : DropTarget

    /** Another row, to group the two together. This is how a group gets created by dragging. */
    data class Row(val nodeId: String) : DropTarget

    /** One of the three outcome blocks. */
    data class Lane(val branch: RuleBranch) : DropTarget
}
