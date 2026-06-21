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

    /** Wraps an existing [MutableBuilderCondition] as a leaf node. */
    class Leaf(val inner: MutableBuilderCondition) : MutableConditionNode {
        override val id: String get() = inner.id
        override var joinToPrevious: String
            get() = inner.joinToPrevious
            set(value) { inner.joinToPrevious = value }
    }

    /** A parenthesized group of child nodes. */
    class Group(
        override val id: String,
        nodes: List<MutableConditionNode> = emptyList(),
        joinToPrevious: String = "",
    ) : MutableConditionNode {
        val nodes: SnapshotStateList<MutableConditionNode> = nodes.toMutableStateList()
        override var joinToPrevious by mutableStateOf(joinToPrevious)
    }
}

/** Converts a [MutableConditionNode] tree to an immutable [BuilderConditionNode] tree. */
fun MutableConditionNode.toImmutable(): BuilderConditionNode = when (this) {
    is MutableConditionNode.Leaf -> inner.toImmutable()
    is MutableConditionNode.Group -> BuilderConditionNode.Group(
        nodeId = id,
        nodes = nodes.map { it.toImmutable() },
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
    joinToPrevious: String = "",
) {
    var field by mutableStateOf(field)
    var operator by mutableStateOf(operator)
    var value by mutableStateOf(value)
    /** Second value used only when operator is "between". */
    var valueTo by mutableStateOf(valueTo)
    /** List items used only when operator is "in" / "containsAny" / "containsAll". */
    val listItems: SnapshotStateList<String> = listItems.toMutableStateList()
    /** Join word (`and` or `or`) placed before this condition in the generated DSL. */
    var joinToPrevious by mutableStateOf(joinToPrevious)

    fun toImmutable(): BuilderCondition = BuilderCondition(
        id = id,
        field = field,
        operator = operator,
        value = value,
        valueTo = valueTo,
        listItems = listItems.toList(),
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
    var name by mutableStateOf(name)
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
class BuilderEditorState private constructor(
    val ruleId: String,
    val conditionNodes: SnapshotStateList<MutableConditionNode>,
    val actions: SnapshotStateList<MutableBuilderAction>,
    val isLocked: Boolean,
    val lockReason: String,
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
            )
            is BuilderRule.Unsupported -> BuilderEditorState(
                ruleId = rule.id,
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                isLocked = true,
                lockReason = rule.reason,
            )
            BuilderRule.None -> BuilderEditorState(
                ruleId = "",
                conditionNodes = mutableStateListOf(),
                actions = mutableStateListOf(),
                isLocked = true,
                lockReason = "No rule selected.",
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
                    joinToPrevious = joinToPrevious,
                )
            )
            is BuilderConditionNode.Group -> MutableConditionNode.Group(
                id = nodeId,
                nodes = nodes.map { it.toMutable() },
                joinToPrevious = joinToPrevious,
            )
        }
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

    /**
     * Ensures an action has exactly [count] arguments, padding with empty strings
     * or trimming extras.
     */
    fun resizeActionArguments(action: MutableBuilderAction, count: Int) {
        while (action.arguments.size < count) {
            action.arguments.add("")
        }
        while (action.arguments.size > count) {
            action.arguments.removeAt(index = action.arguments.lastIndex)
        }
    }
}