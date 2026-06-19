package ruleengine.dsl.ast

data class ComparisonExpressionAst(
    val left: ValueExpressionAst,
    val operator: ComparisonOperatorAst,
    val right: ValueExpressionAst,
    val ignoreCase: Boolean = false
) : ExpressionAst
