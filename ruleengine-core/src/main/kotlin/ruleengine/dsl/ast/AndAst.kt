package ruleengine.dsl.ast

data class AndAst(
    val children: List<ExpressionAst>
) : ExpressionAst
