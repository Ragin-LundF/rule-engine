package ruleengine.dsl.ast.value

data class FunctionCallValueAst(
    val name: String,
    val arguments: List<ValueExpressionAst>
) : ValueExpressionAst
