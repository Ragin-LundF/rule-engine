package ruleengine.dsl.ast.condition

data class NotAst(
    val child: ExpressionAst
) : ExpressionAst
