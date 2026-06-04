package ruleengine.dsl.ast

data class NotAst(
    val child: ExpressionAst
) : ExpressionAst
