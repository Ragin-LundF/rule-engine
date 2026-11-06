package ruleengine.intellij.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import ruleengine.intellij.lexer.RuleTokenTypes

/**
 * Reads field paths from the flat token PSI of a rule file.
 *
 * A path is `IDENTIFIER (. IDENTIFIER)*`, and may continue after a filter: in `a.b[x > 1].c` the `c`
 * belongs to the path `a.b.c`.
 */
object RulePathReader {
    /** The path from its start up to and including [segment]: `reports.income` when `income` is clicked. */
    fun pathUpTo(segment: PsiElement): String {
        val segments = mutableListOf(segment.text)
        var previous = previousSegment(segment = segment)
        while (previous != null) {
            segments.add(0, previous.text)
            previous = previousSegment(segment = previous)
        }
        return segments.joinToString(separator = ".")
    }

    /** The segment before `.segment`, skipping a filter in between; null at the start of the path. */
    private fun previousSegment(segment: PsiElement): PsiElement? {
        val dot = segment.prevSibling?.takeIf { it.type == RuleTokenTypes.DOT } ?: return null
        val before = dot.prevSibling ?: return null
        val identifier = when (before.type) {
            RuleTokenTypes.RBRACKET -> matchingOpen(close = before)?.prevSibling
            else -> before
        }
        return identifier?.takeIf { it.type == RuleTokenTypes.IDENTIFIER }
    }

    /** The collection path a filter applies to, when [element] sits inside `path[...]`. */
    fun enclosingCollection(element: PsiElement): String? {
        var depth = 0
        var current = element.prevSibling
        while (current != null) {
            when (current.type) {
                RuleTokenTypes.RBRACKET -> depth++
                RuleTokenTypes.LBRACKET -> if (depth == 0) {
                    return current.prevSibling?.takeIf { it.type == RuleTokenTypes.IDENTIFIER }?.let(::pathUpTo)
                } else {
                    depth--
                }
            }
            current = current.prevSibling
        }
        return null
    }

    /** True for the local names of `joinByKey("k", credit: credits.amount)`, which are no schema fields. */
    fun isLocalName(element: PsiElement): Boolean {
        var next = element.nextSibling
        while (next != null && next.type == TokenType.WHITE_SPACE) {
            next = next.nextSibling
        }
        return next?.type == RuleTokenTypes.COLON
    }

    private fun matchingOpen(close: PsiElement): PsiElement? {
        var depth = 0
        var current = close.prevSibling
        while (current != null) {
            when (current.type) {
                RuleTokenTypes.RBRACKET -> depth++
                RuleTokenTypes.LBRACKET -> if (depth == 0) return current else depth--
            }
            current = current.prevSibling
        }
        return null
    }

    private val PsiElement.type: IElementType
        get() = node.elementType
}
