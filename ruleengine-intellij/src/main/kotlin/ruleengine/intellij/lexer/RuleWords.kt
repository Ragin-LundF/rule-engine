package ruleengine.intellij.lexer

import com.intellij.psi.tree.IElementType
import ruleengine.core.domain.OperatorNames
import ruleengine.evaluator.compiled.DslFunctions

/**
 * Sorts a bare word into a token type.
 *
 * Mirrors the word sets of `ui.dsl.SyntaxHighlighter` in ruleengine-ui. They are private there, and
 * the UI module is kept unchanged, so the three short DSL keyword sets are restated here; operators
 * and functions come from core.
 */
object RuleWords {
    private val STRUCTURE =
        setOf("rule", "when", "then", "else", "not_exists", "stop", "description", "set", "add", "to")
    private val LOGIC = setOf("and", "or", "not")
    private val BOOLEANS = setOf("true", "false")
    private val NAMED_OPERATORS = OperatorNames.ALL.toSet() + "ignoreCase"
    private val FUNCTIONS = DslFunctions.allNames().toSet()

    fun typeOf(word: CharSequence): IElementType {
        val text = word.toString()
        return when {
            text.startsWith('$') -> RuleTokenTypes.VARIABLE
            text in STRUCTURE -> RuleTokenTypes.KEYWORD
            text in LOGIC -> RuleTokenTypes.LOGIC
            text in BOOLEANS -> RuleTokenTypes.BOOLEAN
            text in NAMED_OPERATORS -> RuleTokenTypes.NAMED_OPERATOR
            text in FUNCTIONS -> RuleTokenTypes.FUNCTION
            else -> RuleTokenTypes.IDENTIFIER
        }
    }
}
