package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDate
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import java.time.LocalDate

/** Compares a date field to a fixed calendar date. */
class DateComparisonExpression(
    private val field: FieldId,
    private val expected: LocalDate,
    private val op: DateComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = op.name,
                expected = expected.toString()
            )
        )

        val value = context.get(field) as? PreparedDate
        if (value == null) {
            trace?.exit(result = false)
            return false
        }

        val cmp = value.value.compareTo(expected)
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
