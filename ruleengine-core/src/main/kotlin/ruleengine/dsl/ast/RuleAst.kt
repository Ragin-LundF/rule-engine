package ruleengine.dsl.ast

data class RuleAst(
    val id: String,
    val description: String? = null,
    val condition: ExpressionAst,
    val actions: List<ActionAst>
)

sealed interface ExpressionAst

data class AndAst(val children: List<ExpressionAst>) : ExpressionAst
data class OrAst(val children: List<ExpressionAst>) : ExpressionAst
data class NotAst(val child: ExpressionAst) : ExpressionAst

data class ConditionAst(
    val field: String,
    val operator: String,
    val value: LiteralAst,
    /** When true the compiled expression will compare case-insensitively (text operators only). */
    val ignoreCase: Boolean = false
) : ExpressionAst

sealed interface LiteralAst
data class StringLiteral(val value: String) : LiteralAst
data class NumberLiteral(val value: String) : LiteralAst
data class ListLiteral(val items: List<LiteralAst>) : LiteralAst
/** Two numeric bounds for the `between` operator (both inclusive). */
data class BetweenLiteral(val low: String, val high: String) : LiteralAst

data class ActionAst(val name: String, val arguments: List<LiteralAst>)

