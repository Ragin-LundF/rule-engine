package ruleengine.evaluator.compiled

data class ArrayExpressionValue(
    val values: List<ExpressionValue>
) : ExpressionValue
