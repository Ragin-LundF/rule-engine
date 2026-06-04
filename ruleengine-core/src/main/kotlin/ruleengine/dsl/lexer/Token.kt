package ruleengine.dsl.lexer

data class Token(
    val type: TokenType,
    val text: String,
    val line: Int,
    val col: Int
)
