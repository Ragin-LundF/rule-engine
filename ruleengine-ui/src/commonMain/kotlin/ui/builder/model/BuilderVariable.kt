package ui.builder.model

import ruleengine.dsl.ast.AssignmentKindAst

/**
 * A single assignment row rendered in a branch: `set <name> = <expression>` or
 * `add <expression> to <name>`, according to [kind].
 *
 * [expression] is an ordinary [BuilderOperand], so the same chip and nested editors that build a
 * comparison side build the value of an assignment, whichever kind it is.
 */
data class BuilderVariable(
    val id: String,
    val name: String,
    val expression: BuilderOperand,
    val kind: AssignmentKindAst = AssignmentKindAst.SET,
)
