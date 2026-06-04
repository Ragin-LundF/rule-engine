package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.TraceCollector

class TextEndsWithExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            NodeMeta(
                type = NodeType.CONDITION, field = field.value,
                operator = if (ignoreCase) "endsWithIgnoreCase" else "endsWith", expected = expectedNormalized
            )
        )

        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText
        if (v == null) {
            trace?.exit(false)
            return false
        }

        val res = if (ignoreCase) {
            v.normalized.endsWith(expectedNormalized, ignoreCase = true)
        } else {
            v.normalized.endsWith(expectedNormalized)
        }

        trace?.exit(res)
        return res
    }
}

