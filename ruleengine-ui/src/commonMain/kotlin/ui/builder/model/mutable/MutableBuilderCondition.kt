package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import ui.builder.model.BuilderConditionNode

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

    fun toImmutable(): BuilderConditionNode.Condition = BuilderConditionNode.Condition(
        nodeId = id,
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
