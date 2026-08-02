package ui.builder.model.mutable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ruleengine.dsl.ast.AssignmentKindAst
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderVariable

/**
 * Mutable editor state for a single assignment row in Builder mode — a `set` or an `add`.
 *
 * [expression] is replaced wholesale rather than mutated, matching how [MutableBuilderComparison]
 * treats its two sides. [kind] is editable in place: switching a row between `set` and `add` keeps
 * the name and the value the author already typed.
 */
class MutableBuilderVariable(
    val id: String,
    name: String,
    expression: BuilderOperand,
    kind: AssignmentKindAst = AssignmentKindAst.SET,
) {
    var name by mutableStateOf(value = name)
    var expression by mutableStateOf(value = expression)
    var kind by mutableStateOf(value = kind)

    fun toImmutable(): BuilderVariable = BuilderVariable(
        id = id,
        name = name,
        expression = expression,
        kind = kind,
    )
}
