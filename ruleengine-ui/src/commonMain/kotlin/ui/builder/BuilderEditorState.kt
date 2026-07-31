package ui.builder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * A mutable node in the builder condition tree.
 */
sealed interface MutableConditionNode {
    val id: String
    var joinToPrevious: String
    var negated: Boolean

    /** Wraps an existing [MutableBuilderCondition] as a leaf node. */
    class Leaf(val inner: MutableBuilderCondition) : MutableConditionNode {
        override val id: String get() = inner.id
        override var joinToPrevious: String
            get() = inner.joinToPrevious
            set(value) {
                inner.joinToPrevious = value
            }
        override var negated: Boolean
            get() = inner.negated
            set(value) {
                inner.negated = value
            }
    }

    /** Wraps a [MutableBuilderComparison] — the leaf kind that can hold computed operands. */
    class ComparisonLeaf(val inner: MutableBuilderComparison) : MutableConditionNode {
        override val id: String get() = inner.id
        override var joinToPrevious: String
            get() = inner.joinToPrevious
            set(value) {
                inner.joinToPrevious = value
            }
        override var negated: Boolean
            get() = inner.negated
            set(value) {
                inner.negated = value
            }
    }

    /** A parenthesized group of child nodes. */
    class Group(
        override val id: String,
        nodes: List<MutableConditionNode> = emptyList(),
        joinToPrevious: String = "",
        negated: Boolean = false,
    ) : MutableConditionNode {
        val nodes: SnapshotStateList<MutableConditionNode> = nodes.toMutableStateList()
        override var joinToPrevious by mutableStateOf(value = joinToPrevious)
        override var negated by mutableStateOf(value = negated)
    }
}

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

/**
 * Mutable editor state for a single condition row in Builder mode.
 * Changes here are reflected back to DSL text via [BuilderToRuleDsl].
 */
class MutableBuilderCondition(
    val id: String,
    field: String,
    operator: String,
    value: String,
    valueTo: String = "",
    listItems: List<String> = emptyList(),
    ignoreCase: Boolean = false,
    negated: Boolean = false,
    joinToPrevious: String = "",
) {
    var field by mutableStateOf(value = field)
    var operator by mutableStateOf(value = operator)
    var value by mutableStateOf(value = value)

    /** Second value used only when operator is "between". */
    var valueTo by mutableStateOf(value = valueTo)

    /** List items used only when operator is "in" / "containsAny" / "containsAll". */
    val listItems: SnapshotStateList<String> = listItems.toMutableStateList()

    /** Case-insensitive comparison; only meaningful for text and string-set operators. */
    var ignoreCase by mutableStateOf(value = ignoreCase)

    /** Renders as `not <condition>` when true. */
    var negated by mutableStateOf(value = negated)

    /** Join word (`and` or `or`) placed before this condition in the generated DSL. */
    var joinToPrevious by mutableStateOf(value = joinToPrevious)

    fun toImmutable(): BuilderCondition = BuilderCondition(
        id = id,
        field = field,
        operator = operator,
        value = value,
        valueTo = valueTo,
        listItems = listItems.toList(),
        ignoreCase = ignoreCase,
        negated = negated,
        joinToPrevious = joinToPrevious,
    )
}

/**
 * Mutable editor state for a comparison row — the leaf kind whose sides can be computed values.
 *
 * Both operands are single `mutableStateOf` slots holding immutable [BuilderOperand] trees; edits
 * replace the tree rather than mutating inside it, so an aggregate six segments deep needs no extra
 * observable state.
 */
class MutableBuilderComparison(
    val id: String,
    left: BuilderOperand,
    operator: String,
    right: BuilderOperand,
    ignoreCase: Boolean = false,
    negated: Boolean = false,
    joinToPrevious: String = "",
) {
    var left by mutableStateOf(value = left)
    var operator by mutableStateOf(value = operator)
    var right by mutableStateOf(value = right)
    var ignoreCase by mutableStateOf(value = ignoreCase)
    var negated by mutableStateOf(value = negated)
    var joinToPrevious by mutableStateOf(value = joinToPrevious)

    fun toImmutable(): BuilderConditionNode.Comparison = BuilderConditionNode.Comparison(
        nodeId = id,
        left = left,
        operator = operator,
        right = right,
        ignoreCase = ignoreCase,
        negated = negated,
        joinToPrevious = joinToPrevious,
    )
}

/**
 * Mutable editor state for a single action row in Builder mode.
 */
class MutableBuilderAction(
    val id: String,
    name: String,
    arguments: List<String> = emptyList(),
) {
    var name by mutableStateOf(value = name)
    val arguments: SnapshotStateList<String> = arguments.toMutableStateList()

    fun toImmutable(): BuilderAction = BuilderAction(
        id = id,
        name = name,
        arguments = arguments.toList(),
    )
}

/**
 * Top-level mutable state for the Builder editor.
 *
 * Created from a [BuilderRule.Supported] snapshot; changes are serialised back to DSL text
 * via [BuilderToRuleDsl.generate] and written to the Code editor's text field.
 *
 * When [isLocked] is true the rule cannot be edited in Builder mode (unsupported syntax).
 */
/** Why Builder mode is unavailable, so the message can be chosen without matching on reason text. */
enum class BuilderLockKind {
    /** Not locked. */
    NONE,

    /** No rule is selected yet. */
    NO_RULE_SELECTED,

    /** The rule uses a construct the Builder cannot represent. */
    UNSUPPORTED_SYNTAX,
}

class BuilderEditorState private constructor(
    val ruleId: String,
    val conditionNodes: SnapshotStateList<MutableConditionNode>,
    val actions: SnapshotStateList<MutableBuilderAction>,
    val isLocked: Boolean,
    val lockReason: String,
    val lockKind: BuilderLockKind = BuilderLockKind.NONE,
) {
    private var nextConditionId = conditionNodes.size + 1
    private var nextActionId = actions.size + 1

    companion object {
        fun fromBuilderRule(rule: BuilderRule): BuilderEditorState = when (rule) {
            is BuilderRule.Supported -> BuilderEditorState(
                ruleId = rule.id,
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
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                isLocked = true,
                lockReason = rule.reason,
                lockKind = BuilderLockKind.UNSUPPORTED_SYNTAX,
            )

            BuilderRule.None -> BuilderEditorState(
                ruleId = "",
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                isLocked = true,
                lockReason = "No rule selected.",
                lockKind = BuilderLockKind.NO_RULE_SELECTED,
            )
        }

        private fun BuilderConditionNode.toMutable(): MutableConditionNode = when (this) {
            is BuilderCondition -> MutableConditionNode.Leaf(
                MutableBuilderCondition(
                    id = id,
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
        defaultOperator: String = "equals"
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
    fun addCondition(defaultField: String = "", defaultOperator: String = "equals"): MutableBuilderCondition {
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
