package ruleengine.evaluator.compiled

import ruleengine.core.domain.dto.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedTemporal
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/** Compares a `date` or `date_time` field to a fixed value of the same type. */
class DateComparisonExpression(
    private val field: FieldId,
    private val expected: PreparedTemporal<*>,
    private val op: DateComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = op.name,
                expected = expected.value.toString()
            )
        )

        val value = context.get(field) as? PreparedTemporal<*>
        if (value == null) {
            trace?.exit(result = false)
            return false
        }

        val cmp = value.compareWith(other = expected)
        val result = when (op) {
            DateComparisonOperator.EQ -> cmp == 0
            DateComparisonOperator.GT -> cmp > 0
            DateComparisonOperator.GTE -> cmp >= 0
            DateComparisonOperator.LT -> cmp < 0
            DateComparisonOperator.LTE -> cmp <= 0
        }

        trace?.exit(result = result)
        return result
    }
}

enum class DateComparisonOperator { EQ, GT, GTE, LT, LTE }
