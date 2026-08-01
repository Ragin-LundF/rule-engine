package ruleengine.evaluator.compiled

import java.math.BigDecimal

data class NumberExpressionValue(
    val value: BigDecimal
) : ExpressionValue
