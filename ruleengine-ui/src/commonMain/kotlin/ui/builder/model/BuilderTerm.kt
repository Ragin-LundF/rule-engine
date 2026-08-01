package ui.builder.model


/** One term of a [BuilderOperand.Calc]; [operator] is empty for the first term. */
data class BuilderTerm(
    val operator: String,
    val operand: BuilderOperand,
)
