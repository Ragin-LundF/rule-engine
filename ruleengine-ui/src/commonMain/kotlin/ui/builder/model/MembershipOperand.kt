package ui.builder.model

import ui.builder.OperandText


/**
 * Reads the right side of a membership test from what the author typed: either a written-out list, or
 * the name of the field or variable holding one.
 *
 * A single bare identifier is a name — that is what makes `[customerId in priorityCustomerIds]`
 * possible — and anything carrying a comma, a quote or a space is a list of values. That one
 * distinction is why membership keeps a single text box instead of an operand chip: the two shapes
 * are told apart by what is typed, not by a kind the author picks first.
 */
fun membershipOperand(text: String): BuilderOperand {
    val trimmed = text.trim()
    val looksLikeName = trimmed.isNotEmpty() && trimmed.none { char -> char == ',' || char == '"' || char == ' ' }
    if (looksLikeName) {
        return pathOperand(dotted = trimmed)
    }
    return BuilderOperand.ListLiteral(
        items = trimmed.split(",")
            .map { item -> item.trim().removeSurrounding(delimiter = "\"") }
            .filter { item -> item.isNotEmpty() },
    )
}

/** What the membership box shows for [operand] — the inverse of [membershipOperand]. */
fun membershipText(operand: BuilderOperand): String = when (operand) {
    is BuilderOperand.ListLiteral -> operand.items.joinToString(separator = ", ")
    is BuilderOperand.FieldRef -> operand.path.names.joinToString(separator = ".")
    is BuilderOperand.Literal -> operand.text
    // An aggregate, a calculation or a call cannot be reached through this box, but a rule may carry
    // one on the right of an `in`. Showing its DSL keeps the row readable rather than blank; editing
    // the box replaces it, which is what the author asked for by typing.
    else -> OperandText.toDsl(operand = operand)
}
