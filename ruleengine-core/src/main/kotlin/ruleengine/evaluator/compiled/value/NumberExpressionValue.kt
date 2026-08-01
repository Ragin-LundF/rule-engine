package ruleengine.evaluator.compiled.value

import java.math.BigDecimal

data class NumberExpressionValue(
    val value: BigDecimal
) : ExpressionValue
