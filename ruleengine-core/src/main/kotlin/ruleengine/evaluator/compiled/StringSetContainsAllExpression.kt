package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedStringSet
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

class StringSetContainsAllExpression(
    private val field: FieldId,
    private val expectedNormalized: Set<String>,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM
    private val matchSet: Set<String> = if (ignoreCase) {
        expectedNormalized.mapTo(destination = HashSet()) { it.lowercase() }
    } else {
        expectedNormalized
    }

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = "containsAll",
                expected = expectedNormalized
            )
        )

        val v = context.get(field) as? PreparedStringSet
        if (v == null) {
            trace?.exit(result = false)
            return false
        }

        val checkSet = if (ignoreCase) v.normalized.mapTo(destination = HashSet()) {
            it.lowercase()
        } else v.normalized
        val res = matchSet.all { it in checkSet }
        trace?.exit(result = res)
        return res
    }
}

