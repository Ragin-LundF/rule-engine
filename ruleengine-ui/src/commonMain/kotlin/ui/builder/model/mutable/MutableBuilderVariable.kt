package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderVariable

/**
 * Mutable editor state for a single `set` row in Builder mode.
 *
 * [expression] is replaced wholesale rather than mutated, matching how [MutableBuilderComparison]
 * treats its two sides.
 */
class MutableBuilderVariable(
    val id: String,
    name: String,
    expression: BuilderOperand,
) {
    var name by mutableStateOf(value = name)
    var expression by mutableStateOf(value = expression)

    fun toImmutable(): BuilderVariable = BuilderVariable(
        id = id,
        name = name,
        expression = expression,
    )
}
