package ruleengine.dsl.ast.value

data class ArithmeticValueAst(
    val left: ValueExpressionAst,
    val operator: ArithmeticOperatorAst,
    val right: ValueExpressionAst
) : ValueExpressionAst
