package ruleengine.dsl.lexer

enum class TokenType {
    IDENT, STRING, NUMBER,
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA,
    EOF
}
