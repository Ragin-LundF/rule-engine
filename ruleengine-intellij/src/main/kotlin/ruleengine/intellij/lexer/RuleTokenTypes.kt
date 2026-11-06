package ruleengine.intellij.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import ruleengine.intellij.lang.RuleLanguage

object RuleTokenTypes {
    val COMMENT = IElementType("COMMENT", RuleLanguage)
    val STRING = IElementType("STRING", RuleLanguage)
    val NUMBER = IElementType("NUMBER", RuleLanguage)
    val IDENTIFIER = IElementType("IDENTIFIER", RuleLanguage)
    val VARIABLE = IElementType("VARIABLE", RuleLanguage)
    val KEYWORD = IElementType("KEYWORD", RuleLanguage)
    val LOGIC = IElementType("LOGIC", RuleLanguage)
    val BOOLEAN = IElementType("BOOLEAN", RuleLanguage)
    val NAMED_OPERATOR = IElementType("NAMED_OPERATOR", RuleLanguage)
    val FUNCTION = IElementType("FUNCTION", RuleLanguage)
    val OPERATOR = IElementType("OPERATOR", RuleLanguage)
    val LBRACE = IElementType("LBRACE", RuleLanguage)
    val RBRACE = IElementType("RBRACE", RuleLanguage)
    val LPAREN = IElementType("LPAREN", RuleLanguage)
    val RPAREN = IElementType("RPAREN", RuleLanguage)
    val LBRACKET = IElementType("LBRACKET", RuleLanguage)
    val RBRACKET = IElementType("RBRACKET", RuleLanguage)
    val COMMA = IElementType("COMMA", RuleLanguage)
    val COLON = IElementType("COLON", RuleLanguage)
    val DOT = IElementType("DOT", RuleLanguage)

    val COMMENTS: TokenSet = TokenSet.create(COMMENT)
    val STRINGS: TokenSet = TokenSet.create(STRING)
}
