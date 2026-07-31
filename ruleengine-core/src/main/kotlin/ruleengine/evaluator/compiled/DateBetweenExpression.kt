package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDate
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import java.time.LocalDate

/** Inclusive date range: `low <= value <= high`. */
class DateBetweenExpression(
    private val field: FieldId,
    private val low: LocalDate,
    private val high: LocalDate
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = "between",
                expected = "$low..$high"
            )
        )

        val value = context.get(field) as? PreparedDate
        if (value == null) {
            trace?.exit(result = false)
            return false
        }

        val result = !value.value.isBefore(low) && !value.value.isAfter(high)
        trace?.exit(result = result)
        return result
    }
}
