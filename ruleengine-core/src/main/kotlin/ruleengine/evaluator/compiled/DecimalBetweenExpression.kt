package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import java.math.BigDecimal

class DecimalBetweenExpression(
    private val field: FieldId,
    private val low: BigDecimal,
    private val high: BigDecimal
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = "between",
                expected = "$low..$high"
            )
        )

        val v = context.get(field) as? PreparedDecimal
        if (v == null) {
            trace?.exit(result = false)
            return false
        }

        val res = v.value in low..high
        trace?.exit(result = res)
        return res
    }
}

