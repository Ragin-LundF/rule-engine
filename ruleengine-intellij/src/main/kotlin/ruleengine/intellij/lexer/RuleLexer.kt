package ruleengine.intellij.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * The IDE lexer for rule files.
 *
 * Follows the token rules of core's `ruleengine.dsl.lexer.Lexer`, but works on offsets and never
 * throws: an editor lexes half-typed text all the time. An unknown character becomes
 * BAD_CHARACTER and an unterminated string ends at the line break. It keeps no state between
 * tokens, so the IDE can restart it at any token.
 */
class RuleLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int {
        return 0
    }

    override fun getTokenType(): IElementType? {
        return tokenType
    }

    override fun getTokenStart(): Int {
        return tokenStart
    }

    override fun getTokenEnd(): Int {
        return tokenEnd
    }

    override fun getBufferSequence(): CharSequence {
        return buffer
    }

    override fun getBufferEnd(): Int {
        return bufferEnd
    }

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= bufferEnd) {
            tokenType = null
            return
        }
        tokenType = readToken(first = buffer[tokenStart])
    }

    private fun readToken(first: Char): IElementType {
        val single = SINGLE_CHAR_TOKENS[first]
        return when {
            single != null -> consume(length = 1, type = single)
            first.isWhitespace() -> consumeWhile(type = TokenType.WHITE_SPACE) { it.isWhitespace() }
            first == '#' -> consumeWhile(type = RuleTokenTypes.COMMENT) { it != '\n' }
            first == '"' -> readString()
            else -> readOperandOrSign(first = first)
        }
    }

    private fun readOperandOrSign(first: Char): IElementType {
        return when {
            first == '-' && charAt(tokenStart + 1).isDigit() -> readNumber(from = tokenStart + 1)
            first == '-' -> consume(length = 1, type = RuleTokenTypes.OPERATOR)
            first.isDigit() -> readNumber(from = tokenStart)
            first in COMPARISON_STARTS -> readComparison()
            first.isLetter() || first == '_' || first == '$' -> readWord()
            else -> consume(length = 1, type = TokenType.BAD_CHARACTER)
        }
    }

    private fun readComparison(): IElementType {
        val length = if (charAt(tokenStart + 1) == '=') 2 else 1
        return consume(length = length, type = RuleTokenTypes.OPERATOR)
    }

    private fun readString(): IElementType {
        var pos = tokenStart + 1
        while (!isStringEnd(offset = pos)) {
            pos += if (buffer[pos] == '\\' && !isLineEnd(offset = pos + 1)) 2 else 1
        }
        tokenEnd = if (charAt(pos) == '"') pos + 1 else pos
        return RuleTokenTypes.STRING
    }

    private fun isLineEnd(offset: Int): Boolean {
        return offset >= bufferEnd || buffer[offset] == '\n'
    }

    private fun isStringEnd(offset: Int): Boolean {
        return isLineEnd(offset = offset) || buffer[offset] == '"'
    }

    private fun readNumber(from: Int): IElementType {
        var pos = from
        var seenDot = false
        while (charAt(pos).isDigit() || (charAt(pos) == '.' && !seenDot)) {
            seenDot = seenDot || buffer[pos] == '.'
            pos++
        }
        tokenEnd = pos
        return RuleTokenTypes.NUMBER
    }

    private fun readWord(): IElementType {
        tokenEnd = tokenStart + 1
        consumeWhile(type = RuleTokenTypes.IDENTIFIER, startAt = tokenEnd) { char ->
            char.isLetterOrDigit() || char == '_' || char == '-'
        }
        return RuleWords.typeOf(word = buffer.subSequence(tokenStart, tokenEnd))
    }

    private fun consume(length: Int, type: IElementType): IElementType {
        tokenEnd = tokenStart + length
        return type
    }

    private fun consumeWhile(type: IElementType, startAt: Int = tokenStart, accept: (Char) -> Boolean): IElementType {
        var pos = startAt
        while (pos < bufferEnd && accept(buffer[pos])) {
            pos++
        }
        tokenEnd = pos
        return type
    }

    private fun charAt(offset: Int): Char {
        return if (offset < bufferEnd) buffer[offset] else '\u0000'
    }

    private companion object {
        val COMPARISON_STARTS = setOf('=', '!', '>', '<')
        val SINGLE_CHAR_TOKENS = mapOf(
            '{' to RuleTokenTypes.LBRACE,
            '}' to RuleTokenTypes.RBRACE,
            '(' to RuleTokenTypes.LPAREN,
            ')' to RuleTokenTypes.RPAREN,
            '[' to RuleTokenTypes.LBRACKET,
            ']' to RuleTokenTypes.RBRACKET,
            ',' to RuleTokenTypes.COMMA,
            ':' to RuleTokenTypes.COLON,
            '.' to RuleTokenTypes.DOT,
            '+' to RuleTokenTypes.OPERATOR,
            '*' to RuleTokenTypes.OPERATOR,
            '/' to RuleTokenTypes.OPERATOR,
        )
    }
}
