package ui.builder.model

/**
 * A single `set` row rendered in the THEN block: `set <name> = <expression>`.
 *
 * [expression] is an ordinary [BuilderOperand], so the same chip and nested editors that build a
 * comparison side build the right-hand side of an assignment.
 */
data class BuilderVariable(
    val id: String,
    val name: String,
    val expression: BuilderOperand,
)
