package ruleengine.dsl

import kotlin.test.Test
import kotlin.test.assertEquals
import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.TokenType

class LexerTest {

    private fun tokenTypes(input: String): List<TokenType> {
        return Lexer(input = input).tokenize().map { it.type }
    }

    private fun tokenTexts(input: String): List<String> {
        return Lexer(input = input).tokenize().map { it.text }
    }

    @Test
    fun `tokenizes function call with gt operator`() {
        val types = tokenTypes("count(transactions) > 100")
        assertEquals(
            expected = listOf(
                TokenType.IDENT, TokenType.LPAREN, TokenType.IDENT, TokenType.RPAREN,
                TokenType.GT, TokenType.NUMBER, TokenType.EOF
            ),
            actual = types
        )
    }

    @Test
    fun `tokenizes function call with gte operator`() {
        val types = tokenTypes("sum(transactions.amount) >= 1000")
        assertEquals(
            expected = listOf(
                TokenType.IDENT, TokenType.LPAREN,
                TokenType.IDENT, TokenType.DOT, TokenType.IDENT,
                TokenType.RPAREN, TokenType.GTE, TokenType.NUMBER, TokenType.EOF
            ),
            actual = types
        )
    }

    @Test
    fun `tokenizes filtered array path with eqeq operator`() {
        val types = tokenTypes("""transactions[label == "risk"].amount""")
        assertEquals(
            expected = listOf(
                TokenType.IDENT, TokenType.LBRACKET,
                TokenType.IDENT, TokenType.EQEQ, TokenType.STRING,
                TokenType.RBRACKET, TokenType.DOT, TokenType.IDENT,
                TokenType.EOF
            ),
            actual = types
        )
    }

    @Test
    fun `tokenizes arithmetic expression with lte operator`() {
        val types = tokenTypes("amount + fee * 2 <= 100")
        assertEquals(
            expected = listOf(
                TokenType.IDENT, TokenType.PLUS,
                TokenType.IDENT, TokenType.STAR, TokenType.NUMBER,
                TokenType.LTE, TokenType.NUMBER, TokenType.EOF
            ),
            actual = types
        )
    }

    @Test
    fun `tokenizes all new operator token texts correctly`() {
        val texts = tokenTexts("a == b != c > d >= e < f <= g")
        assertEquals(
            expected = listOf("a", "==", "b", "!=", "c", ">", "d", ">=", "e", "<", "f", "<=", "g", ""),
            actual = texts
        )
    }

    @Test
    fun `tokenizes arithmetic operators`() {
        val types = tokenTypes("a + b - c * d / e")
        assertEquals(
            expected = listOf(
                TokenType.IDENT, TokenType.PLUS,
                TokenType.IDENT, TokenType.MINUS,
                TokenType.IDENT, TokenType.STAR,
                TokenType.IDENT, TokenType.SLASH,
                TokenType.IDENT, TokenType.EOF
            ),
            actual = types
        )
    }

    @Test
    fun `negative number literal still tokenizes as NUMBER`() {
        val tokens = Lexer(input = "-42").tokenize()
        assertEquals(expected = TokenType.NUMBER, actual = tokens[0].type)
        assertEquals(expected = "-42", actual = tokens[0].text)
    }

    @Test
    fun `standalone minus tokenizes as MINUS`() {
        val types = tokenTypes("a - 5")
        assertEquals(
            expected = listOf(TokenType.IDENT, TokenType.MINUS, TokenType.NUMBER, TokenType.EOF),
            actual = types
        )
    }

    @Test
    fun `legacy named operators still tokenize as IDENT`() {
        val texts = tokenTexts("amount contains \"foo\"")
        assertEquals(expected = listOf("amount", "contains", "foo", ""), actual = texts)
        val types = tokenTypes("amount contains \"foo\"")
        assertEquals(
            expected = listOf(TokenType.IDENT, TokenType.IDENT, TokenType.STRING, TokenType.EOF),
            actual = types
        )
    }
}
