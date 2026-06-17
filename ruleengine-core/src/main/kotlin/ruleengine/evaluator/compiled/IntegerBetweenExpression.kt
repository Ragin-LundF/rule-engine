package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

class IntegerBetweenExpression(
    private val field: FieldId,
    private val low: Long,
    private val high: Long
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

        val v = context.get(field) as? PreparedInteger
        if (v == null) {
            trace?.exit(result = false)
            return false
        }

        val res = v.value in low..high
        trace?.exit(result = res)
        return res
    }
}

