package ruleengine.intellij.diagram

import ruleengine.intellij.lexer.RuleLexer
import ruleengine.intellij.lexer.RuleTokenTypes

/**
 * Cuts the text of one rule out of a rule file: from a top-level `rule` keyword to its closing brace.
 *
 * Parsing only that rule keeps the diagram working while another rule in the same file is half-typed.
 */
object RuleSegments {
    /** The rule containing [offset]; between rules the one before it, before the first rule the first one. */
    fun ruleTextAt(text: CharSequence, offset: Int): String? {
        val ranges = ruleRanges(text = text)
        val range = ranges.firstOrNull { offset in it }
            ?: ranges.lastOrNull { it.first <= offset }
            ?: ranges.firstOrNull()
            ?: return null
        return text.subSequence(range.first, range.last + 1).toString()
    }

    private fun ruleRanges(text: CharSequence): List<IntRange> {
        val lexer = RuleLexer()
        lexer.start(text)
        val ranges = mutableListOf<IntRange>()
        var depth = 0
        var start = -1
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            if (depth == 0 && type == RuleTokenTypes.KEYWORD && lexer.tokenText == "rule") {
                closeOpenRange(ranges = ranges, start = start, end = lexer.tokenStart - 1)
                start = lexer.tokenStart
            }
            if (type == RuleTokenTypes.LBRACE) {
                depth++
            }
            if (type == RuleTokenTypes.RBRACE && depth > 0) {
                depth--
                if (depth == 0 && start >= 0) {
                    ranges += start until lexer.tokenEnd
                    start = -1
                }
            }
            lexer.advance()
        }
        closeOpenRange(ranges = ranges, start = start, end = text.length - 1)
        return ranges
    }

    /** A rule whose closing brace is not typed yet runs up to the next rule or the end of the file. */
    private fun closeOpenRange(ranges: MutableList<IntRange>, start: Int, end: Int) {
        if (start >= 0) {
            ranges += start..end
        }
    }
}
