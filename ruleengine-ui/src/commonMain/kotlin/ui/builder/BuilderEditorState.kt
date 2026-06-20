package ui.builder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

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
) {
    var field by mutableStateOf(field)
    var operator by mutableStateOf(operator)
    var value by mutableStateOf(value)
    /** Second value used only when operator is "between". */
    var valueTo by mutableStateOf(valueTo)
    /** List items used only when operator is "in" / "containsAny" / "containsAll". */
    val listItems: SnapshotStateList<String> = listItems.toMutableStateList()

    fun toImmutable(): BuilderCondition = BuilderCondition(
        id = id,
        field = field,
        operator = operator,
        value = value,
        valueTo = valueTo,
        listItems = listItems.toList(),
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
    val conditions: MutableList<MutableBuilderCondition>,
    val actions: MutableList<MutableBuilderAction>,
    val conditionJoin: ConditionJoin,
    val isLocked: Boolean,
    val lockReason: String,
) {
    private var nextConditionId = conditions.size + 1
    private var nextActionId = actions.size + 1

    companion object {
        fun fromBuilderRule(rule: BuilderRule): BuilderEditorState = when (rule) {
            is BuilderRule.Supported -> BuilderEditorState(
                ruleId = rule.id,
                conditions = rule.conditions.map {
                    MutableBuilderCondition(
                        id = it.id,
                        field = it.field,
                        operator = it.operator,
                        value = it.value,
                        valueTo = it.valueTo,
                        listItems = it.listItems,
                    )
                }.toMutableList(),
                actions = rule.actions.map {
                    MutableBuilderAction(
                        id = it.id,
                        name = it.name,
                        arguments = it.arguments,
                    )
                }.toMutableList(),
                conditionJoin = rule.conditionJoin,
                isLocked = false,
                lockReason = "",
            )
            is BuilderRule.Unsupported -> BuilderEditorState(
                ruleId = rule.id,
                conditions = mutableListOf(),
                actions = mutableListOf(),
                conditionJoin = ConditionJoin.SINGLE,
                isLocked = true,
                lockReason = rule.reason,
            )
            BuilderRule.None -> BuilderEditorState(
                ruleId = "",
                conditions = mutableListOf(),
                actions = mutableListOf(),
                conditionJoin = ConditionJoin.SINGLE,
                isLocked = true,
                lockReason = "No rule selected.",
            )
        }
    }

    /** Adds a new empty condition after the existing ones. */
    fun addCondition(defaultField: String = "", defaultOperator: String = "equals"): MutableBuilderCondition {
        val condition = MutableBuilderCondition(
            id = "cond-${nextConditionId++}",
            field = defaultField,
            operator = defaultOperator,
            value = "",
        )
        conditions.add(condition)
        return condition
    }

    /** Removes the condition with the given [id]. */
    fun removeCondition(id: String) {
        conditions.removeAll { it.id == id }
    }

    /** Adds a new empty action. */
    fun addAction(defaultName: String = ""): MutableBuilderAction {
        val action = MutableBuilderAction(
            id = "act-${nextActionId++}",
            name = defaultName,
            arguments = emptyList(),
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
