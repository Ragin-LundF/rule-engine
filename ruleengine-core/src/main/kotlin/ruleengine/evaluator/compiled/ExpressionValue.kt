package ruleengine.evaluator.compiled

import java.math.BigDecimal

sealed interface ExpressionValue

data class NumberExpressionValue(
    val value: BigDecimal
) : ExpressionValue

data class TextExpressionValue(
    val value: String
) : ExpressionValue

data class BooleanExpressionValue(
    val value: Boolean
) : ExpressionValue

data class ArrayExpressionValue(
    val values: List<ExpressionValue>
) : ExpressionValue

data object MissingExpressionValue : ExpressionValue

data object ObjectExpressionValue : ExpressionValue
