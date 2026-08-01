package ruleengine.evaluator.compiled.value

data class ArrayExpressionValue(
    val values: List<ExpressionValue>
) : ExpressionValue
