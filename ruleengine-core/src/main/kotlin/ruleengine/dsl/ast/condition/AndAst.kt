package ruleengine.dsl.ast.condition

data class AndAst(
    val children: List<ExpressionAst>
) : ExpressionAst
