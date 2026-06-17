package ruleengine.evaluator.context.dto

import java.math.BigDecimal

data class PreparedDecimal(
    val value: BigDecimal
) : PreparedValue
