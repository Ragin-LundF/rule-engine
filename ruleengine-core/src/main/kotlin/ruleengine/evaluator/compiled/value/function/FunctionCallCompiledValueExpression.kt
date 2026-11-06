package ruleengine.evaluator.compiled.value.function

import ruleengine.evaluator.compiled.AggregateFunctionName
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.value.LiteralCompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValues
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.NumericInput
import ruleengine.evaluator.context.PreparedRuleContext
import java.math.BigDecimal
import java.math.MathContext
import java.time.temporal.ChronoUnit

class FunctionCallCompiledValueExpression(
    private val function: AggregateFunctionName,
    private val arguments: List<CompiledValueExpression>
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE

    /**
     * The root names the arguments read, decided at compile time; null when an argument may read the
     * current element of a filter in any other way (a filter, a variable, arithmetic, …).
     */
    private val readRoots: List<String>? = rootsReadBy(arguments = arguments)

    /**
     * True for an aggregate over a path that is collection-valued by its declared shape. Such a path
     * collapses a selection of one element to a scalar, so the aggregate reads it back as a
     * one-element collection — otherwise a single unreadable value made `sum` missing, while the same
     * value next to other elements was skipped.
     */
    private val aggregatesCollection: Boolean = function.isAggregate &&
        (arguments.singleOrNull() as? FieldAccessCompiledValueExpression)?.yieldsCollection == true

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        // Inside `transactions[amount > avg(transactions.amount) * 3]` the average is the same for
        // every element; memoizing it per element made the filter quadratic.
        val sharedAcrossElements = readRoots?.all { root -> context.readsFromDocument(name = root) } == true
        val cache = if (sharedAcrossElements) context.documentCache else context.cache
        return cache.getOrPut(key = this) { computeValue(context = context) }
    }

    private fun rootsReadBy(arguments: List<CompiledValueExpression>): List<String>? {
        return arguments.flatMap { argument ->
            when (argument) {
                is LiteralCompiledValueExpression -> emptyList()
                is FieldAccessCompiledValueExpression -> listOf(argument.plainRootName ?: return null)
                else -> return null
            }
        }
    }

    /**
     * Each arm evaluates the arguments it needs rather than evaluating them up front: the `when`
     * stays exhaustive over the registry, so a new function is a compile error here, and a function
     * that never reads its second argument never pays for it.
     */
    private fun computeValue(context: PreparedRuleContext): ExpressionValue {
        return when (function) {
            AggregateFunctionName.COUNT -> evaluateCount(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.SUM -> evaluateSum(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.SUBTRACT -> evaluateSubtract(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.AVG -> evaluateAvg(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.MEDIAN -> evaluateMedian(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.MAX -> evaluateMax(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.MIN -> evaluateMin(argValue = aggregatedArgument(context = context))
            AggregateFunctionName.ABS -> evaluateAbs(argValue = argumentAt(index = 0, context = context))
            AggregateFunctionName.DAYS_BETWEEN -> evaluateDaysBetween(
                first = argumentAt(index = 0, context = context),
                second = argumentAt(index = 1, context = context)
            )

            AggregateFunctionName.IS_AVAILABLE -> evaluateIsAvailable(
                argValue = argumentAt(index = 0, context = context)
            )

            AggregateFunctionName.DEFAULT -> evaluateDefault(context = context)
        }
    }

    private fun aggregatedArgument(context: PreparedRuleContext): ExpressionValue {
        val value = argumentAt(index = 0, context = context)
        if (!aggregatesCollection || value is ArrayExpressionValue || value is MissingExpressionValue) {
            return value
        }
        return ArrayExpressionValue(values = listOf(value))
    }

    /**
     * The value, or the fallback when the value is missing — including a value the record carries in
     * a form its declared type cannot read, which the engine reads as missing everywhere. A missing
     * fallback leaves the result missing. The fallback is only evaluated when it is needed.
     */
    private fun evaluateDefault(context: PreparedRuleContext): ExpressionValue {
        val value = argumentAt(index = 0, context = context)
        if (value !is MissingExpressionValue) {
            return value
        }
        return argumentAt(index = 1, context = context)
    }

    private fun argumentAt(index: Int, context: PreparedRuleContext): ExpressionValue {
        return arguments.getOrNull(index = index)?.evaluate(context = context) ?: MissingExpressionValue
    }

    /**
     * One of the two functions that consume a missing value instead of propagating it; `isEmpty` is
     * the other.
     *
     * Everything else answers `MissingExpressionValue` when its input is missing, which is what makes
     * a comparison over it undecidable. These two answer a plain boolean — which is exactly why they
     * can guard a rule whose other conditions would otherwise be undecidable, and why they have to be
     * the only exceptions: a rule needs somewhere to stand that is never itself undecidable.
     *
     * An empty collection is unavailable too: it arrives here as an empty [ArrayExpressionValue],
     * whether read whole (`transactions`) or projected (`transactions.amount`). An absent one —
     * at the root or nested, such as `account.transactions` with `account` present — arrives as
     * `MissingExpressionValue`. Both answer `false`; `isEmpty` is what tells them apart.
     */
    private fun evaluateIsAvailable(argValue: ExpressionValue): ExpressionValue {
        val available = when (argValue) {
            is MissingExpressionValue -> false
            is ArrayExpressionValue -> argValue.values.isNotEmpty()
            else -> true
        }
        return BooleanExpressionValue(value = available)
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

    /**
     * How many values the argument holds.
     *
     * An empty collection counts `0` and a scalar counts `1`; both are values the record carries. A
     * **missing** argument counts nothing at all — it propagates, like every other function here. It
     * used to answer `0`, which made "counted nothing" indistinguishable from "had nothing to count"
     * and told a rule with a `not_exists` branch that the condition was decided when the record was
     * empty of the data it asked about.
     */
    private fun evaluateCount(argValue: ExpressionValue): ExpressionValue {
        return when (argValue) {
            is ArrayExpressionValue -> NumberExpressionValue(value = BigDecimal(argValue.values.size))
            is MissingExpressionValue -> MissingExpressionValue
            else -> NumberExpressionValue(value = BigDecimal.ONE)
        }
    }

    private fun evaluateSum(argValue: ExpressionValue): ExpressionValue {
        if (argValue is ArrayExpressionValue) {
            val total = toNumericList(argValue = argValue).fold(initial = BigDecimal.ZERO) { sum, number ->
                sum.add(number)
            }
            return NumberExpressionValue(value = total)
        }
        return numberOf(value = argValue)?.let { number -> NumberExpressionValue(value = number) }
            ?: MissingExpressionValue
    }

    /**
     * The first value less every value after it.
     *
     * The empty case and the missing case are answered separately, and deliberately: subtracting
     * nothing from nothing is `0`, the identity the fold starts from, while a missing argument has no
     * fold to run and propagates. Reading both off `toNumericList` — which flattens either to an empty
     * list — is what used to collapse them onto `0`.
     */
    private fun evaluateSubtract(argValue: ExpressionValue): ExpressionValue {
        if (argValue is MissingExpressionValue) {
            return MissingExpressionValue
        }
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
            is ArrayExpressionValue -> argValue.values.mapNotNull { element -> numberOf(value = element) }
            else -> listOfNotNull(numberOf(value = argValue))
        }
    }

    /**
     * A value read as a number the way `sumByKey` and declared fields read input: a collection member
     * arrives as the text the record held, so `"10"` counts as `10`. Anything else is skipped.
     */
    private fun numberOf(value: ExpressionValue): BigDecimal? {
        return when (value) {
            is NumberExpressionValue -> value.value
            is TextExpressionValue -> NumericInput.decimalOrNull(raw = value.value)
            else -> null
        }
    }
}
