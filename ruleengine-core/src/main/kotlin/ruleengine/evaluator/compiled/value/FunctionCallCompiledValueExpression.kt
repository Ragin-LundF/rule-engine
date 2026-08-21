package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValues
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import java.math.BigDecimal
import java.math.MathContext
import java.time.temporal.ChronoUnit

class FunctionCallCompiledValueExpression(
    private val function: AggregateFunctionName,
    private val arguments: List<CompiledValueExpression>
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return context.cache.getOrPut(key = this) { computeValue(context = context) }
    }

    /**
     * Each arm evaluates the arguments it needs rather than evaluating them up front: the `when`
     * stays exhaustive over the registry, so a new function is a compile error here, and a function
     * that never reads its second argument never pays for it.
     */
    private fun computeValue(context: PreparedRuleContext): ExpressionValue {
        return when (function) {
            AggregateFunctionName.COUNT -> evaluateCount(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.SUM -> evaluateSum(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.SUBTRACT -> evaluateSubtract(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.AVG -> evaluateAvg(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.MEDIAN -> evaluateMedian(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.MAX -> evaluateMax(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.MIN -> evaluateMin(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.ABS -> evaluateAbs(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.DAYS_BETWEEN -> evaluateDaysBetween(
                first = argumentAt(index = 0, context = context),
                second = argumentAt(index = 1, context = context)
            )

            AggregateFunctionName.IS_AVAILABLE -> evaluateIsAvailable(
                argValue = argumentAt(index = 0, context = context)
            )
        }
    }

    private fun argumentAt(index: Int, context: PreparedRuleContext): ExpressionValue {
        return arguments.getOrNull(index = index)?.evaluate(context = context) ?: MissingExpressionValue
    }

    /**
     * The one function that consumes a missing value instead of propagating it.
     *
     * Everything else here answers `MissingExpressionValue` when its input is missing, which is what
     * makes a comparison over it undecidable. This answers `false` — a real boolean — which is exactly
     * why it can guard a rule whose other conditions would otherwise be undecidable.
     */
    private fun evaluateIsAvailable(argValue: ExpressionValue): ExpressionValue {
        return BooleanExpressionValue(value = argValue !is MissingExpressionValue)
    }

    /**
     * Magnitude of a number. A missing input stays missing rather than becoming zero — the two mean
     * different things, and every other function here draws the same distinction.
     */
    private fun evaluateAbs(argValue: ExpressionValue): ExpressionValue {
        return when (argValue) {
            is NumberExpressionValue -> NumberExpressionValue(value = argValue.value.abs())
            else -> MissingExpressionValue
        }
    }

    /**
     * Calendar days from [first] to [second], signed, so a second operand that comes later is
     * positive. Either operand missing or unreadable as a date yields a missing result, which lets a
     * rule guard on the value instead of failing the evaluation.
     */
    private fun evaluateDaysBetween(first: ExpressionValue, second: ExpressionValue): ExpressionValue {
        val from = ExpressionValues.asDate(value = first) ?: return MissingExpressionValue
        val to = ExpressionValues.asDate(value = second) ?: return MissingExpressionValue
        return NumberExpressionValue(value = BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to)))
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
