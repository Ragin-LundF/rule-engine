package ruleengine.evaluator.compiled.value.result

import java.math.BigDecimal

data class NumberExpressionValue(
    val value: BigDecimal
) : ExpressionValue
