package ui.workbench

import ui.builder.BuilderAction
import ui.builder.BuilderConditionNode
import ui.builder.BuilderRule
import ui.builder.OperandText

// How a rule reads as one line of table text. Pure string shaping, split from the composables
// that place it: the table is the only caller, but nothing here draws anything.

internal fun conditionValuePart(value: String, valueTo: String, listItems: List<String>): String = when {
    listItems.isNotEmpty() -> "[${listItems.joinToString(separator = ", ")}]"
    valueTo.isNotBlank() -> "$value … $valueTo"
    else -> value
}

internal fun BuilderConditionNode.toSummaryLines(indent: String = ""): List<String> {
    val join = if (joinToPrevious.isNotBlank()) "${joinToPrevious.uppercase()} " else ""
    val not = if (negated) "NOT " else ""
    return when (this) {
        is BuilderConditionNode.Condition -> {
            val valuePart = conditionValuePart(value = value, valueTo = valueTo, listItems = listItems)
            listOf("$indent$join$not$field $operator $valuePart")
        }
        is BuilderConditionNode.Comparison -> {
            val left = OperandText.toLabel(operand = left)
            val right = OperandText.toLabel(operand = right)
            listOf("$indent$join$not$left $operator $right")
        }
        is BuilderConditionNode.Group -> {
            listOf("$indent$join$not(") +
                nodes.flatMap { it.toSummaryLines(indent = "$indent  ") } +
                listOf("$indent)")
        }
    }
}

internal fun BuilderAction.toDisplaySummary(): String {
    val args = arguments.joinToString(separator = ", ")
    return if (args.isEmpty()) name else "$name($args)"
}

internal fun BuilderRule.tableRuleId(): String = when (this) {
    is BuilderRule.Supported -> id
    is BuilderRule.Unsupported -> id
    BuilderRule.None -> ""
}

/**
 * Null for a rule the Builder could not map: it never read that rule's body, so reporting "no
 * description" would be a claim it cannot make — the clause may well be there in the file.
 */
internal fun BuilderRule.tableDescription(): String? = when (this) {
    is BuilderRule.Supported -> description
    is BuilderRule.Unsupported -> null
    BuilderRule.None -> null
}
