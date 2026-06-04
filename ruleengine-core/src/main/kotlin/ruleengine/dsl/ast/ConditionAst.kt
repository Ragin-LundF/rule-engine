package ruleengine.dsl.ast

data class ConditionAst(
    val field: String,
    val operator: String,
    val value: LiteralAst,
    /** When true the compiled expression will compare case-insensitively (text operators only). */
    val ignoreCase: Boolean = false
) : ExpressionAst
