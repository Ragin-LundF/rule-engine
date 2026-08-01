package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import ui.builder.OperatorOptions
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderLockKind
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderRule
import ui.builder.model.pathOperand

/** Converts a [MutableConditionNode] tree to an immutable [BuilderConditionNode] tree. */
fun MutableConditionNode.toImmutable(): BuilderConditionNode = when (this) {
    is MutableConditionNode.Leaf -> inner.toImmutable()
    is MutableConditionNode.ComparisonLeaf -> inner.toImmutable()
    is MutableConditionNode.Group -> BuilderConditionNode.Group(
        nodeId = id,
        nodes = nodes.map { it.toImmutable() },
        negated = negated,
        joinToPrevious = joinToPrevious,
    )
}

class BuilderEditorState private constructor(
    val ruleId: String,
    description: String,
    val conditionNodes: SnapshotStateList<MutableConditionNode>,
    val actions: SnapshotStateList<MutableBuilderAction>,
    val isLocked: Boolean,
    val lockReason: String,
    val lockKind: BuilderLockKind = BuilderLockKind.NONE,
) {
    /** The rule's optional `description` clause. Editable, unlike [ruleId], which is renamed separately. */
    var description by mutableStateOf(value = description)

    private var nextConditionId = conditionNodes.size + 1
    private var nextActionId = actions.size + 1

    companion object {
        fun fromBuilderRule(rule: BuilderRule): BuilderEditorState = when (rule) {
            is BuilderRule.Supported -> BuilderEditorState(
                ruleId = rule.id,
                description = rule.description,
                conditionNodes = rule.conditionNodes.map { it.toMutable() }.toMutableStateList(),
                actions = rule.actions.map {
                    MutableBuilderAction(
                        id = it.id,
                        name = it.name,
                        arguments = it.arguments,
                    )
                }.toMutableStateList(),
                isLocked = false,
                lockReason = "",
                lockKind = BuilderLockKind.NONE,
            )

            is BuilderRule.Unsupported -> BuilderEditorState(
                ruleId = rule.id,
                description = "",
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                isLocked = true,
                lockReason = rule.reason,
                lockKind = BuilderLockKind.UNSUPPORTED_SYNTAX,
            )

            BuilderRule.None -> BuilderEditorState(
                ruleId = "",
                description = "",
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                isLocked = true,
                lockReason = "No rule selected.",
                lockKind = BuilderLockKind.NO_RULE_SELECTED,
            )
        }

        private fun BuilderConditionNode.toMutable(): MutableConditionNode = when (this) {
            is BuilderConditionNode.Condition -> MutableConditionNode.Leaf(
                MutableBuilderCondition(
                    id = nodeId,
                    field = field,
                    operator = operator,
                    value = value,
                    valueTo = valueTo,
                    listItems = listItems,
                    ignoreCase = ignoreCase,
                    negated = negated,
                    joinToPrevious = joinToPrevious,
                )
            )

            is BuilderConditionNode.Comparison -> MutableConditionNode.ComparisonLeaf(
                MutableBuilderComparison(
                    id = nodeId,
                    left = left,
                    operator = operator,
                    right = right,
                    ignoreCase = ignoreCase,
                    negated = negated,
                    joinToPrevious = joinToPrevious,
                )
            )

            is BuilderConditionNode.Group -> MutableConditionNode.Group(
                id = nodeId,
                nodes = nodes.map { it.toMutable() },
                joinToPrevious = joinToPrevious,
                negated = negated,
            )
        }
    }

    /**
     * Removes the group with the given [id] and inserts its children at the
     * group's original position. The first child inherits the group's joinToPrevious.
     * Does nothing if no group with that id exists.
     */
    fun ungroup(id: String) {
        val groupIndex = conditionNodes.indexOfFirst { it.id == id && it is MutableConditionNode.Group }
        if (groupIndex < 0) return
        val group = conditionNodes[groupIndex] as MutableConditionNode.Group
        if (group.nodes.isNotEmpty()) {
            group.nodes.first().joinToPrevious = group.joinToPrevious
        }
        conditionNodes.removeAt(groupIndex)
        conditionNodes.addAll(index = groupIndex, elements = group.nodes.toList())
    }

    /**
     * Groups a set of condition nodes, identified by their IDs, into a single group node.
     * If the set contains fewer than two valid condition nodes, no action is taken.
     *
     * @param ids A set of IDs representing the condition nodes to be grouped. Only IDs that can
     *            be matched to existing condition nodes are considered.
     */
    fun groupConditions(ids: Set<String>) {
        if (ids.size < 2) return
        val indices = ids.mapNotNull { id ->
            conditionNodes.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }.sorted()
        if (indices.size < 2) return

        val nodesToWrap = indices.map { conditionNodes[it] }
        val groupJoin = nodesToWrap.first().joinToPrevious
        val group = MutableConditionNode.Group(
            id = "grp-${nextConditionId++}",
            nodes = nodesToWrap,
            joinToPrevious = groupJoin,
        )

        // Remove from back to front so indices stay valid
        indices.sortedDescending().forEach { conditionNodes.removeAt(it) }
        // Insert the group at the position of the first wrapped node
        conditionNodes.add(index = indices.first(), element = group)
    }

    /**
     * Adds a new empty condition inside the group with the given [groupId].
     * If the group doesn't exist, does nothing.
     */
    fun addConditionInside(
        groupId: String,
        defaultField: String = "",
        defaultOperator: String = OperatorOptions.EQUALS
    ): MutableBuilderCondition? {
        val group = findGroupById(groupId) ?: return null
        val condition = MutableBuilderCondition(
            id = "cond-${nextConditionId++}",
            field = defaultField,
            operator = defaultOperator,
            value = "",
            joinToPrevious = group.nodes.lastOrNull()?.let { "and" } ?: "",
        )
        group.nodes.add(MutableConditionNode.Leaf(condition))
        return condition
    }

    private fun findGroupById(id: String): MutableConditionNode.Group? {
        for (node in conditionNodes) {
            if (node is MutableConditionNode.Group && node.id == id) return node
        }
        return null
    }

    /** Adds a new empty condition after the existing ones, at the top level. */
    fun addCondition(
        defaultField: String = "",
        defaultOperator: String = OperatorOptions.EQUALS,
    ): MutableBuilderCondition {
        val condition = MutableBuilderCondition(
            id = "cond-${nextConditionId++}",
            field = defaultField,
            operator = defaultOperator,
            value = "",
            joinToPrevious = conditionNodes.lastOrNull()?.let { "and" } ?: "",
        )
        conditionNodes.add(MutableConditionNode.Leaf(condition))
        return condition
    }

    /** Removes the leaf or group with the given [id] from the top level. */
    fun removeCondition(id: String) {
        conditionNodes.removeAll { it.id == id }
    }

    /**
     * Replaces the node with the given [id] anywhere in the tree, preserving its position.
     * Returns true when a node was replaced.
     */
    fun replaceNode(id: String, replacement: MutableConditionNode): Boolean =
        replaceIn(nodes = conditionNodes, id = id, replacement = replacement)

    private fun replaceIn(
        nodes: SnapshotStateList<MutableConditionNode>,
        id: String,
        replacement: MutableConditionNode,
    ): Boolean {
        val index = nodes.indexOfFirst { it.id == id }
        if (index >= 0) {
            replacement.joinToPrevious = nodes[index].joinToPrevious
            nodes[index] = replacement
            return true
        }
        return nodes.filterIsInstance<MutableConditionNode.Group>().any { group ->
            replaceIn(nodes = group.nodes, id = id, replacement = replacement)
        }
    }

    /**
     * Converts the simple condition row with the given [id] into a comparison row, so its sides can
     * hold aggregates or calculations. The field becomes the left operand and the value the right.
     * Returns the new comparison, or null when [id] is not a simple condition row.
     */
    fun toComparison(id: String, operator: String): MutableBuilderComparison? {
        val leaf = findLeaf(nodes = conditionNodes, id = id) ?: return null
        val condition = leaf.inner
        val comparison = MutableBuilderComparison(
            id = condition.id,
            left = pathOperand(dotted = condition.field),
            operator = operator,
            right = BuilderOperand.Literal(
                text = condition.value,
                numeric = condition.value.trim().toDoubleOrNull() != null,
            ),
            negated = condition.negated,
            joinToPrevious = condition.joinToPrevious,
        )
        return if (replaceNode(id = id, replacement = MutableConditionNode.ComparisonLeaf(inner = comparison))) {
            comparison
        } else {
            null
        }
    }

    private fun findLeaf(
        nodes: List<MutableConditionNode>,
        id: String,
    ): MutableConditionNode.Leaf? {
        for (node in nodes) {
            when (node) {
                is MutableConditionNode.Leaf -> if (node.id == id) return node
                is MutableConditionNode.Group -> findLeaf(nodes = node.nodes, id = id)?.let { return it }
                is MutableConditionNode.ComparisonLeaf -> Unit
            }
        }
        return null
    }

    /** Adds a new comparison row after the existing ones, at the top level. */
    fun addComparison(
        left: BuilderOperand,
        operator: String,
        right: BuilderOperand,
    ): MutableBuilderComparison {
        val comparison = MutableBuilderComparison(
            id = "cmp-${nextConditionId++}",
            left = left,
            operator = operator,
            right = right,
            joinToPrevious = conditionNodes.lastOrNull()?.let { "and" } ?: "",
        )
        conditionNodes.add(MutableConditionNode.ComparisonLeaf(inner = comparison))
        return comparison
    }

    /** Adds a new empty action with the given number of default arguments. */
    fun addAction(defaultName: String = "", defaultArgCount: Int = 0): MutableBuilderAction {
        val action = MutableBuilderAction(
            id = "act-${nextActionId++}",
            name = defaultName,
            arguments = List(defaultArgCount) { "" }.toMutableList(),
        )
        actions.add(action)
        return action
    }

    /** Removes the action with the given [id]. */
    fun removeAction(id: String) {
        actions.removeAll { it.id == id }
    }
}
