package ruleengine.dsl.ast

data class FunctionCallValueAst(
    val name: String,
    val arguments: List<ValueExpressionAst>
) : ValueExpressionAst
