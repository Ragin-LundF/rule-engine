package ruleengine.dsl

import ruleengine.dsl.lexer.Lexer
import ruleengine.dsl.lexer.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun `aggregate function names tokenize as IDENT`() {
        val functions = listOf("count", "sum", "subtract", "avg", "median", "max", "min")
        for (fn in functions) {
            val types = tokenTypes("$fn(transactions) > 0")
            assertEquals(
                expected = TokenType.IDENT,
                actual = types.first(),
                message = "Expected IDENT for $fn"
            )
            val texts = tokenTexts("$fn(transactions) > 0")
            assertEquals(
                expected = fn,
                actual = texts.first(),
                message = "Expected token text '$fn'"
            )
        }
    }

    @Test
    fun `percentage rule expression tokenizes without error`() {
        val input = """sum(transactions[label == "risk"].amount) > sum(transactions[amount > 0].amount) * 0.03"""
        val types = tokenTypes(input)
        assertEquals(expected = TokenType.EOF, actual = types.last())
        assert(TokenType.IDENT in types)
        assert(TokenType.EQEQ in types)
        assert(TokenType.GT in types)
        assert(TokenType.STAR in types)
        assert(TokenType.NUMBER in types)
    }

    @Test
    fun `a colon is a token of its own`() {
        assertEquals(
            expected = listOf(
                TokenType.IDENT, TokenType.COLON, TokenType.IDENT, TokenType.DOT, TokenType.IDENT, TokenType.EOF
            ),
            actual = tokenTypes("credit: credits.amount")
        )
    }

    @Test
    fun `only a backslash before a quote or a backslash is an escape`() {
        assertEquals(
            expected = listOf("^DE\\d{20}$", "say \"hi\"", "C:\\", "a\\.b", ""),
            actual = tokenTexts(input = """"^DE\d{20}$" "say \"hi\"" "C:\\" "a\.b"""")
        )
    }

    @Test
    fun `a number stops before a second dot`() {
        assertEquals(
            expected = listOf(TokenType.NUMBER, TokenType.DOT, TokenType.NUMBER, TokenType.EOF),
            actual = tokenTypes(input = "1.2.3")
        )
        assertEquals(expected = listOf("1.2", ".", "3", ""), actual = tokenTexts(input = "1.2.3"))
    }
}
