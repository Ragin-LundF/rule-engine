package ruleengine.dsl.ast

data class OrAst(
    val children: List<ExpressionAst>
) : ExpressionAst
