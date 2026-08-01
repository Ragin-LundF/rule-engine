package ruleengine.dsl.lexer

import ruleengine.dsl.diagnostics.ParseException

class Lexer(private val input: String) {
    private var pos = 0
    private var line = 1
    private var col = 1

    private fun current(): Char? {
        return if (pos >= input.length) {
            null
        } else {
            input[pos]
        }
    }

    private fun advance(): Char? {
        val c = current() ?: return null
        pos++
        if (c == '\n') {
            line++
            col = 1
        } else {
            col++
        }

        return c
    }

    private fun skipWhitespace() {
        while (true) {
            val c = current() ?: return
            if (c.isWhitespace()) advance() else return
        }
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            skipWhitespace()
            val c = current()
            if (c == null) {
                tokens += Token(type = TokenType.EOF, text = "", line = line, col = col)
                break
            }

            // handle single-character tokens via map to reduce branching
            val singleCharTokens = mapOf(
                '{' to TokenType.LBRACE, '}' to TokenType.RBRACE,
                '(' to TokenType.LPAREN, ')' to TokenType.RPAREN,
                '[' to TokenType.LBRACKET, ']' to TokenType.RBRACKET,
                ',' to TokenType.COMMA, '.' to TokenType.DOT,
                '+' to TokenType.PLUS, '*' to TokenType.STAR, '/' to TokenType.SLASH
            )

            if (singleCharTokens.containsKey(c)) {
                tokens += makeToken(type = singleCharTokens.getValue(key = c), text = c.toString())
                advance()
                continue
            }

            when (c) {
                '"' -> tokens += readString()
                '>', '<', '=', '!', '-' -> tokens += readOperatorOrMinus()
                '#' -> {
                    // Single-line comment — skip everything up to (but not including) the newline.
                    // The newline itself is left for skipWhitespace() so line tracking stays correct.
                    while (current() != null && current() != '\n') {
                        advance()
                    }
                }

                else -> {
                    tokens += if (c.isLetter() || c == '_') {
                        readIdentOrKeyword()
                    } else if (c.isDigit()) {
                        readNumber()
                    } else if (c == '$') {
                        // Extraction reference ($1) or variable reference ($total) – both IDENT
                        readDollarRef()
                    } else {
                        throw ParseException(line = line, column = col, messageText = "Unexpected character: '$c'")
                    }
                }
            }
        }
        return tokens
    }

    private fun makeToken(type: TokenType, text: String): Token {
        return Token(type = type, text = text, line = line, col = col)
    }

    private fun readString(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        advance() // consume '"'
        while (true) {
            val c = current() ?: throw ParseException(
                line = startLine,
                column = startCol,
                messageText = "Unterminated string"
            )

            if (c == '"') {
                advance()
                break
            }

            if (c == '\\') {
                advance()
                val nxt = current() ?: throw ParseException(
                    line = startLine,
                    column = startCol,
                    messageText = "Unterminated escape in string"
                )
                sb.append(nxt)
                advance()
            } else {
                sb.append(c)
                advance()
            }
        }

        return Token(type = TokenType.STRING, text = sb.toString(), line = startLine, col = startCol)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun readIdentOrKeyword(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        while (true) {
            val c = current() ?: break
            if (c.isLetterOrDigit() || c == '_' || c == '-') {
                sb.append(c)
                advance()
            } else {
                break
            }
        }

        return Token(type = TokenType.IDENT, text = sb.toString(), line = startLine, col = startCol)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun readOperatorOrMinus(): Token {
        val startLine = line
        val startCol = col
        val first = current()!!
        advance()
        val next = current()

        // '-' followed by a digit is a negative number literal
        if (first == '-' && next != null && next.isDigit()) {
            val sb = StringBuilder()
            sb.append('-')
            while (current()?.let { it.isDigit() || it == '.' } == true) {
                sb.append(current())
                advance()
            }
            return Token(type = TokenType.NUMBER, text = sb.toString(), line = startLine, col = startCol)
        }

        if (first == '-') {
            return Token(type = TokenType.MINUS, text = "-", line = startLine, col = startCol)
        }

        val twoChar = next == '='
        return when (first) {
            '=' if next == '=' -> {
                advance()
                Token(type = TokenType.EQEQ, text = "==", line = startLine, col = startCol)
            }
            '!' if twoChar -> {
                advance()
                Token(type = TokenType.BANGEQ, text = "!=", line = startLine, col = startCol)
            }
            '>' if twoChar -> {
                advance()
                Token(type = TokenType.GTE, text = ">=", line = startLine, col = startCol)
            }
            '>' -> Token(type = TokenType.GT, text = ">", line = startLine, col = startCol)
            '<' if twoChar -> {
                advance()
                Token(type = TokenType.LTE, text = "<=", line = startLine, col = startCol)
            }
            '<' -> Token(type = TokenType.LT, text = "<", line = startLine, col = startCol)
            else -> {
                // fallback: emit as IDENT for legacy named operators that start with these chars
                val sb = StringBuilder()
                sb.append(first)
                if (twoChar) {
                    sb.append(next)
                    advance()
                }
                Token(type = TokenType.IDENT, text = sb.toString(), line = startLine, col = startCol)
            }
        }
    }

    /**
     * Reads a `$`-prefixed reference: either an extraction reference (`$1`, `$2`, …) or a variable
     * reference (`$orderTotal`). Both are emitted as a single [TokenType.IDENT] token so the parser
     * can recognise them by the leading `$` and tell them apart by whether the rest is all digits.
     *
     * The name part accepts the same characters as a plain identifier, so `$total-2024` is one token.
     */
    @Suppress("LoopWithTooManyJumpStatements")
    private fun readDollarRef(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        // consume the leading '$'
        sb.append(advance())
        while (true) {
            val c = current() ?: break
            if (c.isLetterOrDigit() || c == '_' || c == '-') {
                sb.append(c)
                advance()
            } else {
                break
            }
        }
        return Token(type = TokenType.IDENT, text = sb.toString(), line = startLine, col = startCol)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun readNumber(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        while (true) {
            val c = current() ?: break
            if (c.isDigit() || c == '.') {
                sb.append(c)
                advance()
            } else {
                break
            }
        }

        return Token(type = TokenType.NUMBER, text = sb.toString(), line = startLine, col = startCol)
    }
}
