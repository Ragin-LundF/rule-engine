package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderOperand

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
