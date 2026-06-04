package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.TraceCollector

class TextContainsExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            NodeMeta(
                type = NodeType.CONDITION, field = field.value,
                operator = if (ignoreCase) "containsIgnoreCase" else "contains", expected = expectedNormalized
            )
        )

        val v = context.get(field) as? ruleengine.evaluator.context.PreparedText
        if (v == null) {
            trace?.exit(false)
            return false
        }

        val res = if (ignoreCase) {
            v.normalized.contains(expectedNormalized, ignoreCase = true)
        } else {
            v.normalized.contains(expectedNormalized)
        }

        trace?.exit(res)
        return res
    }
}

