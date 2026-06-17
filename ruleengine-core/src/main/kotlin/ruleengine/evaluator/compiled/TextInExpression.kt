package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedText
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.evaluator.trace.TraceCollector

class TextInExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    private val matchSet: Set<String> = if (ignoreCase) {
        expectedNormalized.mapTo(destination = HashSet()) { it.lowercase() }
    } else {
        expectedNormalized
    }

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION, field = field.value,
                operator = if (ignoreCase) "inIgnoreCase" else "in", expected = expectedNormalized
            )
        )

        val v = context.get(field) as? PreparedText
        if (v == null) {
            trace?.exit(result = false)
            return false
        }

        val key = if (ignoreCase) v.normalized.lowercase() else v.normalized
        val res = key in matchSet

        trace?.exit(result = res)
        return res
    }
}

