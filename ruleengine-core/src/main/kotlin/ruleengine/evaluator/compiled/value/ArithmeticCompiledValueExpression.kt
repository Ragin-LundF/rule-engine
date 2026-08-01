package ruleengine.evaluator.compiled.value

import ruleengine.dsl.ast.ArithmeticOperatorAst
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext

class ArithmeticCompiledValueExpression(
    private val left: CompiledValueExpression,
    private val operator: ArithmeticOperatorAst,
    private val right: CompiledValueExpression,
    override val cost: EvaluationCost
) : CompiledValueExpression {
    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        val leftValue = left.evaluate(context = context)
        val rightValue = right.evaluate(context = context)
        if (leftValue !is NumberExpressionValue || rightValue !is NumberExpressionValue) {
            return MissingExpressionValue
        }
        val result = when (operator) {
            ArithmeticOperatorAst.ADD -> leftValue.value + rightValue.value
            ArithmeticOperatorAst.SUBTRACT -> leftValue.value - rightValue.value
            ArithmeticOperatorAst.MULTIPLY -> leftValue.value * rightValue.value
            ArithmeticOperatorAst.DIVIDE -> {
                if (rightValue.value.compareTo(java.math.BigDecimal.ZERO) == 0) {
                    return MissingExpressionValue
                }
                leftValue.value.divide(rightValue.value, 10, java.math.RoundingMode.HALF_UP)
            }
        }
        return NumberExpressionValue(value = result)
    }
}
