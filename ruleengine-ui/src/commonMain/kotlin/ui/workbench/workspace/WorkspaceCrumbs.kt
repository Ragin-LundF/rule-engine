package ui.workbench.workspace

import ruleengine.core.domain.dto.RuleBranch
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.selection.SelectionStep
import ui.workbench.model.InspectorItem
import ui.workbench.model.workspace.Crumb

/**
 * Where the author is, as the context bar's trail: file › rule › group › condition › side › level.
 *
 * Derived from the one selection the workbench holds, so the trail cannot disagree with the row that is
 * highlighted or the editor that is open under it. Every part but the file is a selection, so every part
 * but the file can be clicked to go there — and [upOneLevel] is the same trail walked one step back,
 * which is what `Esc` does.
 */
object WorkspaceCrumbs {

    /**
     * The trail for [item] inside the open rule.
     *
     * A selection that no longer resolves — a row edited away — stops the trail at the rule rather
     * than naming something that is not there.
     */
    fun of(
        fileName: String?,
        ruleOrdinal: Int?,
        state: BuilderEditorState,
        item: InspectorItem?,
    ): List<Crumb> {
        if (state.ruleId.isBlank()) {
            return listOfNotNull(fileName?.let { name -> Crumb(label = name, target = null) })
        }
        val rule = InspectorItem.Rule(id = state.ruleId)
        val head = listOfNotNull(
            fileName?.let { name -> Crumb(label = name, target = null) },
            Crumb(label = ruleLabel(ordinal = ruleOrdinal, ruleId = state.ruleId), target = rule),
        )
        return head + when (item) {
            is InspectorItem.Condition -> conditionCrumbs(state = state, item = item, rule = rule)
            is InspectorItem.Statement -> statementCrumbs(state = state, item = item, rule = rule)
            else -> emptyList()
        }
    }

    /**
     * One level up from [item]: out of a nested operand, then out of the row into its group, then out
     * to the rule. Null when there is nowhere further up to go.
     */
    fun upOneLevel(state: BuilderEditorState, item: InspectorItem?): InspectorItem? {
        return when (item) {
            is InspectorItem.Condition -> when {
                item.steps.isNotEmpty() -> item.copy(steps = item.steps.dropLast(n = 1))
                else -> {
                    val chain = chainTo(nodes = state.conditionNodes, id = item.conditionId)
                    val parent = chain?.dropLast(n = 1)?.lastOrNull()?.node
                    parent?.let { group -> InspectorItem.Condition(conditionId = group.id) }
                        ?: InspectorItem.Rule(id = state.ruleId)
                }
            }

            is InspectorItem.Statement -> when {
                item.steps.isNotEmpty() -> item.copy(steps = item.steps.dropLast(n = 1))
                else -> InspectorItem.Rule(id = state.ruleId)
            }

            else -> null
        }
    }

    /**
     * What is being edited, in the words the trail uses: "left value", "condition 2.1", "action".
     *
     * Null when nothing inside the rule is selected, which is when the bar has no mode to report.
     */
    fun editingLabel(state: BuilderEditorState, item: InspectorItem?): String? {
        return when (item) {
            is InspectorItem.Condition -> item.steps.lastOrNull()?.let { step -> stepLabel(step = step) }
                ?: chainTo(nodes = state.conditionNodes, id = item.conditionId)?.let { chain ->
                    nodeLabel(chain = chain)
                }

            is InspectorItem.Statement -> item.steps.lastOrNull()?.let { step -> stepLabel(step = step) }
                ?: statementLabel(state = state, item = item)

            else -> null
        }?.replaceFirstChar { first -> first.lowercaseChar() }
    }

    /** The rule as the navigator numbers it, so the two always name it the same way. */
    private fun ruleLabel(ordinal: Int?, ruleId: String): String {
        return ordinal?.let { number -> "Rule $number · $ruleId" } ?: ruleId
    }

    private fun conditionCrumbs(
        state: BuilderEditorState,
        item: InspectorItem.Condition,
        rule: InspectorItem.Rule,
    ): List<Crumb> {
        val chain = chainTo(nodes = state.conditionNodes, id = item.conditionId) ?: return emptyList()
        val conditions = Crumb(label = "Conditions", target = rule)
        val levels = chain.indices.map { depth ->
            val prefix = chain.take(n = depth + 1)
            Crumb(
                label = nodeLabel(chain = prefix),
                target = InspectorItem.Condition(conditionId = prefix.last().node.id),
            )
        }
        val steps = item.steps.indices.map { index ->
            Crumb(
                label = stepLabel(step = item.steps[index]),
                target = item.copy(steps = item.steps.take(n = index + 1)),
            )
        }
        return listOf(conditions) + levels + steps
    }

    private fun statementCrumbs(
        state: BuilderEditorState,
        item: InspectorItem.Statement,
        rule: InspectorItem.Rule,
    ): List<Crumb> {
        val label = statementLabel(state = state, item = item) ?: return emptyList()
        val lane = Crumb(label = laneLabel(branch = item.branch), target = rule)
        val statement = Crumb(label = label, target = item.copy(steps = emptyList()))
        val steps = item.steps.indices.map { index ->
            Crumb(
                label = stepLabel(step = item.steps[index]),
                target = item.copy(steps = item.steps.take(n = index + 1)),
            )
        }
        return listOf(lane, statement) + steps
    }

    /** "Condition 2.1" or "Group 2" — the number the outline prints beside the row. */
    private fun nodeLabel(chain: List<ChainLink>): String {
        val number = chain.joinToString(separator = ".") { link -> "${link.index + 1}" }
        return when (chain.last().node) {
            is MutableConditionNode.Group -> "Group $number"
            else -> "Condition $number"
        }
    }

    private fun statementLabel(state: BuilderEditorState, item: InspectorItem.Statement): String? {
        val variable = state.variablesOf(branch = item.branch).firstOrNull { it.id == item.statementId }
        if (variable != null) {
            return "Set ${variable.name.ifBlank { "variable" }}"
        }
        val action = state.actionsOf(branch = item.branch).firstOrNull { it.id == item.statementId }
        return action?.let { found -> "Action ${found.name.ifBlank { "(unnamed)" }}" }
    }

    /** The outcome lanes in plain words — what each branch answers, not the keyword that spells it. */
    fun laneLabel(branch: RuleBranch): String {
        return when (branch) {
            RuleBranch.THEN -> "When true"
            RuleBranch.ELSE -> "When false"
            RuleBranch.NOT_EXISTS -> "When data is missing"
        }
    }

    /** A level inside a row, named for what it is to the author rather than how the DSL spells it. */
    private fun stepLabel(step: SelectionStep): String {
        return when (step) {
            SelectionStep.Left -> "Left value"
            SelectionStep.Right -> "Right value"
            SelectionStep.Value -> "Value"
            SelectionStep.Extraction -> "Extract"
            is SelectionStep.Argument -> "Argument ${step.index + 1}"
            is SelectionStep.Term -> "Term ${step.index + 1}"
            is SelectionStep.Segment -> "Path step ${step.index + 1}"
            is SelectionStep.Filter -> "Filter ${step.index + 1}"
        }
    }

    /** The nodes from the top level down to [id], each with its position among its siblings. */
    private fun chainTo(nodes: List<MutableConditionNode>, id: String): List<ChainLink>? {
        nodes.forEachIndexed { index, node ->
            if (node.id == id) {
                return listOf(ChainLink(index = index, node = node))
            }
            if (node is MutableConditionNode.Group) {
                val below = chainTo(nodes = node.nodes, id = id)
                if (below != null) {
                    return listOf(ChainLink(index = index, node = node)) + below
                }
            }
        }
        return null
    }

    /** One level of [chainTo]'s answer. Private: it exists only to carry the index with the node. */
    private class ChainLink(val index: Int, val node: MutableConditionNode)
}
