package ui.workbench.model

import ui.builder.model.selection.SelectionStep

/**
 * What a builder canvas highlights, derived from the one selection the workbench holds.
 *
 * The canvases need this in three separate pieces — a condition id, a statement id, and the path into
 * the expression — because a row and a statement are drawn by different composables and only one of
 * them can be the selected one. [InspectorItem] holds it as a single value instead, which is what keeps
 * the canvas and the Inspector from ever disagreeing about what is selected.
 *
 * The translation lives here rather than at the call site so there is one place where "which of the
 * three is set" is decided. It was three casts inline in `RuleEditor` before, which is both easy to get
 * subtly wrong when a fourth [InspectorItem] arrives and the reason that function outgrew its
 * complexity budget.
 */
data class CanvasSelection(
    val nodeId: String?,
    val statementId: String?,
    val steps: List<SelectionStep>?,
    /** The selected schema field's dotted path, for the areas outside the Builder. */
    val fieldId: String? = null,
    /** The selected action's name, likewise. */
    val actionName: String? = null,
) {
    companion object {

        /** Nothing selected, or something selected that no canvas draws — a rule, a file, a schema. */
        val None: CanvasSelection = CanvasSelection(nodeId = null, statementId = null, steps = null)

        /** The canvas view of [item]. */
        fun of(item: InspectorItem?): CanvasSelection = when (item) {
            is InspectorItem.Condition -> CanvasSelection(
                nodeId = item.conditionId,
                statementId = null,
                steps = item.steps,
            )

            is InspectorItem.Statement -> CanvasSelection(
                nodeId = null,
                statementId = item.statementId,
                steps = item.steps,
            )

            // Not drawn by a builder canvas, but the Schema and Actions docks highlight the lines
            // these own — which is the same question ("what is selected") answered for another surface,
            // so it belongs in the same translation rather than in a cast at the call site.
            is InspectorItem.Field -> CanvasSelection(
                nodeId = null,
                statementId = null,
                steps = null,
                fieldId = item.id,
            )

            is InspectorItem.Action -> CanvasSelection(
                nodeId = null,
                statementId = null,
                steps = null,
                actionName = item.name,
            )

            else -> None
        }
    }
}
