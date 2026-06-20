package ui.builder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mutable editor state for a single condition row in Builder mode.
 * Changes here are reflected back to DSL text via [BuilderToRuleDsl].
 */
class MutableBuilderCondition(
    field: String,
    operator: String,
    value: String,
) {
    var field by mutableStateOf(field)
    var operator by mutableStateOf(operator)
    var value by mutableStateOf(value)
    /** Second value used only when operator is "between". */
    var valueTo by mutableStateOf("")
    /** List items used only when operator is "in" / "containsAny" / "containsAll". */
    val listItems = mutableStateListOf<String>()

    fun toImmutable(): BuilderCondition = BuilderCondition(
        field = field,
        operator = operator,
        value = value,
    )
}

/**
 * Mutable editor state for a single action row in Builder mode.
 */
class MutableBuilderAction(
    name: String,
    arguments: List<String>,
) {
    var name by mutableStateOf(name)
    val arguments = mutableStateListOf<String>().also { it.addAll(arguments) }

    fun toImmutable(): BuilderAction = BuilderAction(name = name, arguments = arguments.toList())
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
    companion object {
        fun fromBuilderRule(rule: BuilderRule): BuilderEditorState = when (rule) {
            is BuilderRule.Supported -> BuilderEditorState(
                ruleId = rule.id,
                conditions = rule.conditions.map {
                    MutableBuilderCondition(
                        field = it.field,
                        operator = it.operator,
                        value = it.value,
                    )
                }.toMutableList(),
                actions = rule.actions.map {
                    MutableBuilderAction(name = it.name, arguments = it.arguments)
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
}
