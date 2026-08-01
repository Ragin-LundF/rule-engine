package ruleengine.evaluator.compiled.value.result

data class ArrayExpressionValue(
    val values: List<ExpressionValue>
) : ExpressionValue
