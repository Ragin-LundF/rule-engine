package ruleengine.dsl.lexer

enum class TokenType {
    IDENT, DOT, STRING, NUMBER,
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, COLON,
    PLUS, MINUS, STAR, SLASH,
    EQEQ, BANGEQ, GT, GTE, LT, LTE,
    EOF
}
