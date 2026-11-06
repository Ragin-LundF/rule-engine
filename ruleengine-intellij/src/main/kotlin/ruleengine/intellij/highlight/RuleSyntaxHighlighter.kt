package ruleengine.intellij.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import ruleengine.intellij.lexer.RuleLexer
import ruleengine.intellij.lexer.RuleTokenTypes

class RuleSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer {
        return RuleLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(COLORS[tokenType])
    }

    private companion object {
        val COLORS: Map<IElementType, TextAttributesKey> = mapOf(
            RuleTokenTypes.KEYWORD to RuleColors.KEYWORD,
            RuleTokenTypes.LOGIC to RuleColors.LOGIC,
            RuleTokenTypes.BOOLEAN to RuleColors.BOOLEAN,
            RuleTokenTypes.STRING to RuleColors.STRING,
            RuleTokenTypes.NUMBER to RuleColors.NUMBER,
            RuleTokenTypes.COMMENT to RuleColors.COMMENT,
            RuleTokenTypes.OPERATOR to RuleColors.OPERATOR,
            RuleTokenTypes.NAMED_OPERATOR to RuleColors.NAMED_OPERATOR,
            RuleTokenTypes.FUNCTION to RuleColors.FUNCTION,
            RuleTokenTypes.VARIABLE to RuleColors.VARIABLE,
            RuleTokenTypes.IDENTIFIER to RuleColors.IDENTIFIER,
            RuleTokenTypes.LBRACE to RuleColors.BRACES,
            RuleTokenTypes.RBRACE to RuleColors.BRACES,
            RuleTokenTypes.LPAREN to RuleColors.PARENTHESES,
            RuleTokenTypes.RPAREN to RuleColors.PARENTHESES,
            RuleTokenTypes.LBRACKET to RuleColors.BRACKETS,
            RuleTokenTypes.RBRACKET to RuleColors.BRACKETS,
            RuleTokenTypes.COMMA to RuleColors.PUNCTUATION,
            RuleTokenTypes.COLON to RuleColors.PUNCTUATION,
            RuleTokenTypes.DOT to RuleColors.PUNCTUATION,
            TokenType.BAD_CHARACTER to RuleColors.BAD_CHARACTER,
        )
    }
}
