package ruleengine.dsl.ast

/** Two numeric bounds for the `between` operator (both inclusive). */
data class BetweenLiteral(
    val low: String,
    val high: String
) : LiteralAst
