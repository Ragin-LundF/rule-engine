package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import java.math.BigDecimal

class FunctionCallCompiledValueExpression(
    private val function: AggregateFunctionName,
    private val argument: CompiledValueExpression
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        val argValue = argument.evaluate(context = context)
        return when (function) {
            AggregateFunctionName.COUNT -> evaluateCount(argValue = argValue)
            AggregateFunctionName.SUM -> evaluateSum(argValue = argValue)
            else -> MissingExpressionValue
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
}
