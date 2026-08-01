package ruleengine.evaluator.compiled.value

import ruleengine.dsl.ast.ComparisonOperatorAst
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.ObjectExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * The comparison used whenever an operand is an expression rather than a bare field — an aggregate
 * (`count(...)`, `sum(...)`), arithmetic, or another field. Plain `field op literal` conditions
 * compile to the dedicated leaves instead ([IntegerComparisonExpression] and friends).
 *
 * @param label Rendered text of the left operand, used to name the condition in the trace. Empty for
 *   the per-element predicate inside a filter segment, which is evaluated with no collector at all.
 */
class ComparisonCompiledExpression(
    private val left: CompiledValueExpression,
    private val operator: ComparisonOperatorAst,
    private val right: CompiledValueExpression,
    override val cost: EvaluationCost,
    private val label: String = ""
) : CompiledExpression {
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        val leftValue = left.evaluate(context = context)
        val rightValue = right.evaluate(context = context)

        // Entered after both operands are evaluated, unlike the other leaves: the values being
        // compared are what make the node worth reading, and they are not known any earlier.
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = label,
                operator = operator.name,
                expected = plainValue(value = rightValue),
                actual = plainValue(value = leftValue)
            )
        )
        val result = compareValues(leftValue = leftValue, operator = operator, rightValue = rightValue)
        trace?.exit(result = result)
        return result
    }

    /**
     * Unwraps to the value Jackson should see. An [ExpressionValue] is a wrapper, so handing one
     * straight to the trace would serialize as `{"value":500}` and display as
     * `NumberExpressionValue(value=500)`.
     */
    private fun plainValue(value: ExpressionValue): Any? {
        return when (value) {
            is NumberExpressionValue -> value.value
            is TextExpressionValue -> value.value
            is BooleanExpressionValue -> value.value
            is ArrayExpressionValue -> value.values.map { element -> plainValue(value = element) }
            MissingExpressionValue, ObjectExpressionValue -> null
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun compareValues(
        leftValue: ExpressionValue,
        operator: ComparisonOperatorAst,
        rightValue: ExpressionValue
    ): Boolean {
        if (leftValue is MissingExpressionValue || rightValue is MissingExpressionValue) {
            return false
        }
        return when (leftValue) {
            is NumberExpressionValue if rightValue is NumberExpressionValue -> {
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

            is TextExpressionValue if rightValue is TextExpressionValue -> {
                when (operator) {
                    ComparisonOperatorAst.EQ -> leftValue.value == rightValue.value
                    ComparisonOperatorAst.NEQ -> leftValue.value != rightValue.value
                    else -> false
                }
            }

            is BooleanExpressionValue if rightValue is BooleanExpressionValue -> {
                when (operator) {
                    ComparisonOperatorAst.EQ -> leftValue.value == rightValue.value
                    ComparisonOperatorAst.NEQ -> leftValue.value != rightValue.value
                    // Ordering has no meaning for booleans.
                    else -> false
                }
            }

            else -> false
        }
    }
}
