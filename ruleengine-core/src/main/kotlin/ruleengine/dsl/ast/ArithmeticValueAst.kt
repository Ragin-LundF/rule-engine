package ruleengine.dsl.ast

data class ArithmeticValueAst(
    val left: ValueExpressionAst,
    val operator: ArithmeticOperatorAst,
    val right: ValueExpressionAst
) : ValueExpressionAst
