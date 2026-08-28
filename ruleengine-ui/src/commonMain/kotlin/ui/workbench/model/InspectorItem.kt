package ui.workbench.model

import ruleengine.core.domain.dto.RuleBranch
import ui.builder.model.selection.SelectionStep

/**
 * An item selected in the inspector panel.
 */
sealed interface InspectorItem {
    /** A field definition from the field schema. */
    data class Field(val id: String) : InspectorItem

    /** An action definition from the action schema. */
    data class Action(val name: String) : InspectorItem

    /** A parsed rule. */
    data class Rule(val id: String) : InspectorItem

    /**
     * A condition row inside Builder mode, and optionally something inside it.
     *
     * [steps] is empty for the row itself and grows as the author drills into an operand, a path
     * segment or a `where` filter — see [SelectionStep]. Keeping the drill path on the *selection*
     * rather than in per-component `remember` state is what lets the inspector be the only editing
     * surface: nothing expands under the row, so the row never moves.
     *
     * Defaulted to empty so every existing call site still selects a whole row unchanged.
     */
    data class Condition(
        val conditionId: String,
        val steps: List<SelectionStep> = emptyList(),
    ) : InspectorItem

    /**
     * An action, `set` or `add` row of one branch, and optionally something inside it.
     *
     * Anchored by [branch] as well as [statementId] because the id alone would not say which block
     * the row belongs to — and `then`, `else` and `not_exists` hold the same kinds of row.
     */
    data class Statement(
        val branch: RuleBranch,
        val statementId: String,
        val steps: List<SelectionStep> = emptyList(),
    ) : InspectorItem

    /** A manifest project. */
    data class Manifest(val name: String) : InspectorItem
}
