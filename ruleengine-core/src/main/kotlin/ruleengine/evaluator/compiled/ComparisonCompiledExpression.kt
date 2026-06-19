package ruleengine.evaluator.compiled

import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector

class ComparisonCompiledExpression(
    private val left: CompiledValueExpression,
    private val operator: ComparisonOperatorAst,
    private val right: CompiledValueExpression,
    override val cost: EvaluationCost
) : CompiledExpression {
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        val leftValue = left.evaluate(context = context)
        val rightValue = right.evaluate(context = context)
        return compareValues(leftValue = leftValue, operator = operator, rightValue = rightValue)
    }

    private fun compareValues(
        leftValue: ExpressionValue,
        operator: ComparisonOperatorAst,
        rightValue: ExpressionValue
    ): Boolean {
        if (leftValue is MissingExpressionValue || rightValue is MissingExpressionValue) {
            return false
        }
        return when {
            leftValue is NumberExpressionValue && rightValue is NumberExpressionValue -> {
                val cmp = leftValue.value.compareTo(rightValue.value)
                when (operator) {
                    ComparisonOperatorAst.EQ -> cmp == 0
                    ComparisonOperatorAst.NEQ -> cmp != 0
                    ComparisonOperatorAst.GT -> cmp > 0
                    ComparisonOperatorAst.GTE -> cmp >= 0
                    ComparisonOperatorAst.LT -> cmp < 0
                    ComparisonOperatorAst.LTE -> cmp <= 0
                }
            }
            leftValue is TextExpressionValue && rightValue is TextExpressionValue -> {
                when (operator) {
                    ComparisonOperatorAst.EQ -> leftValue.value == rightValue.value
                    ComparisonOperatorAst.NEQ -> leftValue.value != rightValue.value
                    else -> false
                }
            }
            else -> false
        }
    }
}
