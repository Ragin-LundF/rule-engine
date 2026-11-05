package ruleengine.dsl.ast.condition

data class OrAst(
    val children: List<ExpressionAst>
) : ExpressionAst
