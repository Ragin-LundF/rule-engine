package ruleengine.dsl.lexer

import ruleengine.dsl.diagnostics.ParseException

data class Token(val type: TokenType, val text: String, val line: Int, val col: Int)

enum class TokenType {
    IDENT, STRING, NUMBER,
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA,
    EOF
}

class Lexer(private val input: String) {
    private var pos = 0
    private var line = 1
    private var col = 1

    private fun current(): Char? = if (pos >= input.length) null else input[pos]

    private fun advance(): Char? {
        val c = current() ?: return null
        pos++
        if (c == '\n') { line++; col = 1 } else col++
        return c
    }

    private fun skipWhitespace() {
        while (true) {
            val c = current() ?: return
            if (c.isWhitespace()) advance() else return
        }
    }

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            skipWhitespace()
            val c = current()
            if (c == null) { tokens += Token(TokenType.EOF, "", line, col); break }

            when (c) {
                '{' -> { tokens += makeToken(TokenType.LBRACE, "{"); advance() }
                '}' -> { tokens += makeToken(TokenType.RBRACE, "}"); advance() }
                '(' -> { tokens += makeToken(TokenType.LPAREN, "("); advance() }
                ')' -> { tokens += makeToken(TokenType.RPAREN, ")"); advance() }
                '[' -> { tokens += makeToken(TokenType.LBRACKET, "["); advance() }
                ']' -> { tokens += makeToken(TokenType.RBRACKET, "]"); advance() }
                ',' -> { tokens += makeToken(TokenType.COMMA, ","); advance() }
                '"' -> tokens += readString()
                '>', '<', '=', '!' -> tokens += readOperator()
                '#' -> {
                    // Single-line comment — skip everything up to (but not including) the newline.
                    // The newline itself is left for skipWhitespace() so line tracking stays correct.
                    while (current() != null && current() != '\n') advance()
                }
                else -> {
                    if (c.isLetter() || c == '_' ) tokens += readIdentOrKeyword()
                    else if (c.isDigit() || c == '-') tokens += readNumber()
                    else throw ParseException(line = line, column = col, messageText = "Unexpected character: '$c'")
                }
            }
        }
        return tokens
    }

    private fun makeToken(type: TokenType, text: String) = Token(type = type, text = text, line = line, col = col)

    private fun readString(): Token {
        val startLine = line; val startCol = col
        val sb = StringBuilder()
        advance() // consume '"'
        while (true) {
            val c = current() ?: throw ParseException(line = startLine, column = startCol, messageText = "Unterminated string")
            if (c == '"') { advance(); break }
            if (c == '\\') {
                advance()
                val nxt = current() ?: throw ParseException(line = startLine, column = startCol, messageText = "Unterminated escape in string")
                sb.append(nxt); advance()
            } else { sb.append(c); advance() }
        }
        return Token(TokenType.STRING, sb.toString(), startLine, startCol)
    }

    private fun readIdentOrKeyword(): Token {
        val startLine = line; val startCol = col
        val sb = StringBuilder()
        while (true) {
            val c = current() ?: break
            if (c.isLetterOrDigit() || c == '_' || c == '-') { sb.append(c); advance() }
            else break
        }
        return Token(TokenType.IDENT, sb.toString(), startLine, startCol)
    }

    private fun readOperator(): Token {
        val startLine = line; val startCol = col
        val sb = StringBuilder()
        val first = current()!!
        sb.append(first); advance()
        val next = current()
        if (next != null && next == '=') {
            sb.append(next); advance()
        }
        return Token(TokenType.IDENT, sb.toString(), startLine, startCol)
    }

    private fun readNumber(): Token {
        val startLine = line; val startCol = col
        val sb = StringBuilder()
        if (current() == '-') { sb.append('-'); advance() }
        while (true) {
            val c = current() ?: break
            if (c.isDigit() || c == '.') { sb.append(c); advance() }
            else break
        }
        return Token(TokenType.NUMBER, sb.toString(), startLine, startCol)
    }
}

