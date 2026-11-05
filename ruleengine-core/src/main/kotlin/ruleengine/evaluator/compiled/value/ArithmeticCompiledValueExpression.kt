package ruleengine.evaluator.compiled.value

import ruleengine.dsl.ast.value.ArithmeticOperatorAst
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

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
                if (rightValue.value.compareTo(BigDecimal.ZERO) == 0) {
                    return MissingExpressionValue
                }
                divide(dividend = leftValue.value, divisor = rightValue.value)
            }
        }
        return NumberExpressionValue(value = result)
    }

    /**
     * Exact when the quotient terminates (`0.123456789012 / 2` keeps all twelve places, `10 / 4` is
     * `2.5`); otherwise rounded to [ROUNDED_SCALE] places half-up, as RULE-SPEC documents.
     */
    private fun divide(dividend: BigDecimal, divisor: BigDecimal): BigDecimal {
        if (terminates(dividend = dividend, divisor = divisor)) {
            return dividend.divide(divisor)
        }
        return dividend.divide(divisor, ROUNDED_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * A quotient has a finite decimal expansion exactly when its reduced denominator has no prime
     * factor but 2 and 5. Checked up front, so a recurring quotient never costs a thrown exception.
     */
    private fun terminates(dividend: BigDecimal, divisor: BigDecimal): Boolean {
        val numerator = dividend.unscaledValue()
        val denominator = divisor.unscaledValue()
        var reduced = denominator.divide(numerator.gcd(denominator)).abs()
        reduced = reduced.shiftRight(reduced.lowestSetBit)
        while (reduced.mod(FIVE) == BigInteger.ZERO) {
            reduced = reduced.divide(FIVE)
        }
        return reduced == BigInteger.ONE
    }

    private companion object {
        const val ROUNDED_SCALE = 10
        val FIVE: BigInteger = BigInteger.valueOf(5L)
    }
}
