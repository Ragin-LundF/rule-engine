package ruleengine.intellij.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleLexerTest {
    @Test
    fun `classifies the words and signs of a rule`() {
        val tokens = lex(text = "rule \"a\" { when not count(x.y) >= -1.5 and \$v == true then stop } # c")

        assertEquals(
            expected = listOf(
                RuleTokenTypes.KEYWORD to "rule", RuleTokenTypes.STRING to "\"a\"", RuleTokenTypes.LBRACE to "{",
                RuleTokenTypes.KEYWORD to "when", RuleTokenTypes.LOGIC to "not", RuleTokenTypes.FUNCTION to "count",
                RuleTokenTypes.LPAREN to "(", RuleTokenTypes.IDENTIFIER to "x", RuleTokenTypes.DOT to ".",
                RuleTokenTypes.IDENTIFIER to "y", RuleTokenTypes.RPAREN to ")", RuleTokenTypes.OPERATOR to ">=",
                RuleTokenTypes.NUMBER to "-1.5", RuleTokenTypes.LOGIC to "and", RuleTokenTypes.VARIABLE to "\$v",
                RuleTokenTypes.OPERATOR to "==", RuleTokenTypes.BOOLEAN to "true", RuleTokenTypes.KEYWORD to "then",
                RuleTokenTypes.KEYWORD to "stop", RuleTokenTypes.RBRACE to "}", RuleTokenTypes.COMMENT to "# c",
            ),
            actual = tokens.filter { it.first != TokenType.WHITE_SPACE },
        )
    }

    @Test
    fun `ends an unterminated string at the line break`() {
        val tokens = lex(text = "label \"open\nthen")

        assertEquals(expected = RuleTokenTypes.STRING to "\"open", actual = tokens[2])
        assertEquals(expected = RuleTokenTypes.KEYWORD to "then", actual = tokens[4])
    }

    @Test
    fun `keeps escaped quotes inside a string`() {
        val tokens = lex(text = "\"a\\\"b\" x")

        assertEquals(expected = RuleTokenTypes.STRING to "\"a\\\"b\"", actual = tokens.first())
    }

    @Test
    fun `covers any input without gaps and marks unknown characters`() {
        val text = "when € ~ \"x\\\n@ 1..2 ${'$'} - ="
        val tokens = lex(text = text)

        assertEquals(expected = text, actual = tokens.joinToString(separator = "") { it.second })
        assertEquals(expected = 3, actual = tokens.count { it.first == TokenType.BAD_CHARACTER })
    }

    private fun lex(text: String): List<Pair<IElementType, String>> {
        val lexer = RuleLexer()
        lexer.start(text)
        val tokens = mutableListOf<Pair<IElementType, String>>()
        while (lexer.tokenType != null) {
            tokens += lexer.tokenType!! to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return tokens
    }
}
