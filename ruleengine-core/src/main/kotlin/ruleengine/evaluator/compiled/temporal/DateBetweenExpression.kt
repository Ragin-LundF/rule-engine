package ruleengine.evaluator.compiled.temporal

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedTemporal
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/** Inclusive range for a `date` or `date_time` field: `low <= value <= high`. */
class DateBetweenExpression(
    private val field: FieldId,
    private val low: PreparedTemporal<*>,
    private val high: PreparedTemporal<*>
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = OperatorNames.BETWEEN,
                expected = "${low.value}..${high.value}"
            )
        )

        val value = context.get(field) as? PreparedTemporal<*>
        if (value == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val result = value.compareWith(other = low) >= 0 && value.compareWith(other = high) <= 0
        val verdict = ConditionVerdict.of(value = result)
        trace?.exit(verdict = verdict)
        return verdict
    }
}
