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
                ',' to TokenType.COMMA
            )

            if (singleCharTokens.containsKey(c)) {
                tokens += makeToken(type = singleCharTokens.getValue(key = c), text = c.toString())
                advance()
                continue
            }

            when (c) {
                '"' -> tokens += readString()
                '>', '<', '=', '!' -> tokens += readOperator()
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
                    } else if (c.isDigit() || c == '-') {
                        readNumber()
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

    private fun readOperator(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        val first = current()!!
        sb.append(first)
        advance()
        val next = current()
        if (next != null && next == '=') {
            sb.append(next)
            advance()
        }

        return Token(type = TokenType.IDENT, text = sb.toString(), line = startLine, col = startCol)
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun readNumber(): Token {
        val startLine = line
        val startCol = col
        val sb = StringBuilder()
        if (current() == '-') {
            sb.append('-')
            advance()
        }

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

