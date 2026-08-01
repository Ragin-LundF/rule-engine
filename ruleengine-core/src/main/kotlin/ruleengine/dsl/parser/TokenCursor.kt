package ruleengine.dsl.parser

import ruleengine.dsl.diagnostics.ParseException
import ruleengine.dsl.lexer.Token
import ruleengine.dsl.lexer.TokenType

/**
 * A read position in a token list, shared by every parser that reads the same source.
 *
 * Extracted so [Parser] can hand the same position to [LiteralParser] and [ThenBlockParser] instead
 * of owning every production itself. [pos] is writable because the parser backtracks: it speculates
 * on a value expression, and restores the position when the speculation fails.
 */
internal class TokenCursor(private val tokens: List<Token>) {

    var pos: Int = 0

    fun current(): Token = tokens.getOrElse(index = pos) { tokens.last() }

    fun advance() {
        if (pos < tokens.size) {
            pos++
        }
    }

    fun expect(type: TokenType): Token {
        val token = current()
        if (token.type != type) {
            throw ParseException(
                line = token.line,
                column = token.col,
                messageText = "Expected $type but found ${token.type} (${token.text})"
            )
        }

        advance()
        return token
    }

    /** Line of the most recently consumed token, or 0 before anything has been consumed. */
    fun previousLine(): Int = if (pos > 0) tokens[pos - 1].line else 0
}
