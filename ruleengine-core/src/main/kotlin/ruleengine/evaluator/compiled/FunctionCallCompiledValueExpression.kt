package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import java.math.BigDecimal
import java.math.MathContext

class FunctionCallCompiledValueExpression(
    private val function: AggregateFunctionName,
    private val argument: CompiledValueExpression
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return context.cache.getOrPut(key = this) { computeValue(context = context) }
    }

    private fun computeValue(context: PreparedRuleContext): ExpressionValue {
        val argValue = argument.evaluate(context = context)
        return when (function) {
            AggregateFunctionName.COUNT -> evaluateCount(argValue = argValue)
            AggregateFunctionName.SUM -> evaluateSum(argValue = argValue)
            AggregateFunctionName.SUBTRACT -> evaluateSubtract(argValue = argValue)
            AggregateFunctionName.AVG -> evaluateAvg(argValue = argValue)
            AggregateFunctionName.MEDIAN -> evaluateMedian(argValue = argValue)
            AggregateFunctionName.MAX -> evaluateMax(argValue = argValue)
            AggregateFunctionName.MIN -> evaluateMin(argValue = argValue)
        }
    }

    private fun evaluateCount(argValue: ExpressionValue): ExpressionValue {
        return when (argValue) {
            is ArrayExpressionValue -> NumberExpressionValue(value = BigDecimal(argValue.values.size))
            is MissingExpressionValue -> NumberExpressionValue(value = BigDecimal.ZERO)
            else -> NumberExpressionValue(value = BigDecimal.ONE)
        }
    }

    private fun evaluateSum(argValue: ExpressionValue): ExpressionValue {
        return when (argValue) {
            is ArrayExpressionValue -> {
                var total = BigDecimal.ZERO
                for (element in argValue.values) {
                    if (element is NumberExpressionValue) {
                        total = total.add(element.value)
                    }
                }
                NumberExpressionValue(value = total)
            }

            is NumberExpressionValue -> argValue
            else -> MissingExpressionValue
        }
    }

    private fun evaluateSubtract(argValue: ExpressionValue): ExpressionValue {
        val numbers = toNumericList(argValue = argValue)
        if (numbers.isEmpty()) {
            return NumberExpressionValue(value = BigDecimal.ZERO)
        }
        var result = numbers[0]
        for (i in 1 until numbers.size) {
            result = result.subtract(numbers[i])
        }
        return NumberExpressionValue(value = result)
    }

    private fun evaluateAvg(argValue: ExpressionValue): ExpressionValue {
        val numbers = toNumericList(argValue = argValue)
        if (numbers.isEmpty()) {
            return MissingExpressionValue
        }
        var total = BigDecimal.ZERO
        for (n in numbers) {
            total = total.add(n)
        }
        return NumberExpressionValue(value = total.divide(BigDecimal(numbers.size), MathContext.DECIMAL128))
    }

    private fun evaluateMedian(argValue: ExpressionValue): ExpressionValue {
        val numbers = toNumericList(argValue = argValue).sorted()
        if (numbers.isEmpty()) {
            return MissingExpressionValue
        }
        val mid = numbers.size / 2
        return if (numbers.size % 2 == 1) {
            NumberExpressionValue(value = numbers[mid])
        } else {
            NumberExpressionValue(
                value = numbers[mid - 1].add(numbers[mid]).divide(BigDecimal(2), MathContext.DECIMAL128)
            )
        }
    }

    private fun evaluateMax(argValue: ExpressionValue): ExpressionValue {
        val numbers = toNumericList(argValue = argValue)
        if (numbers.isEmpty()) {
            return MissingExpressionValue
        }
        var max = numbers[0]
        for (n in numbers) {
            if (n > max) {
                max = n
            }
        }
        return NumberExpressionValue(value = max)
    }

    private fun evaluateMin(argValue: ExpressionValue): ExpressionValue {
        val numbers = toNumericList(argValue = argValue)
        if (numbers.isEmpty()) {
            return MissingExpressionValue
        }
        var min = numbers[0]
        for (n in numbers) {
            if (n < min) {
                min = n
            }
        }
        return NumberExpressionValue(value = min)
    }

    private fun toNumericList(argValue: ExpressionValue): List<BigDecimal> {
        return when (argValue) {
            is ArrayExpressionValue -> argValue.values.filterIsInstance<NumberExpressionValue>().map { it.value }
            is NumberExpressionValue -> listOf(argValue.value)
            else -> emptyList()
        }
    }
}
