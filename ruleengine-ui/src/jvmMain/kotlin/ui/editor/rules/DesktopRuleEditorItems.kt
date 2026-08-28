package ui.editor.rules

import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection

private val DSL_BLOCK_KEYWORDS = setOf("when", "then", "else", "not_exists")

fun dslLineOpensBlock(trimmedLine: String): Boolean {
    return trimmedLine.endsWith(char = '{') || trimmedLine in DSL_BLOCK_KEYWORDS
}

fun autoClosingBraceDedent(text: String, bracePos: Int): Pair<String, Int> {
    val lineStart = text.lastIndexOf(char = '\n', startIndex = bracePos - 1) + 1
    val lineContent = text.substring(startIndex = lineStart, endIndex = bracePos)
    if (lineContent.isEmpty() || !lineContent.all { it == ' ' }) {
        return Pair(text, 0)
    }
    val spacesToRemove = lineContent.length.coerceAtMost(maximumValue = 4)
    val newText = text.substring(
        0, lineStart
    ) + lineContent.drop(n = spacesToRemove) + text.substring(startIndex = bracePos)
    return Pair(first = newText, second = spacesToRemove)
}

fun isContextuallyImmediate(context: DslCursorContext): Boolean {
    val expectsOperator = context.section == DslSection.WHEN &&
            context.precedingField != null && context.precedingOperator == null
    val expectsAction = context.section.isBranch() && context.afterAction == null
    return expectsOperator || expectsAction
}
