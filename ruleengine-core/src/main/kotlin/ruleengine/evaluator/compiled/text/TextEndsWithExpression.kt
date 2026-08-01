package ruleengine.evaluator.compiled.text

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedText
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

class TextEndsWithExpression(
    private val field: FieldId,
    private val expectedNormalized: String,
    private val ignoreCase: Boolean = false
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = if (ignoreCase) "${OperatorNames.ENDS_WITH}IgnoreCase" else OperatorNames.ENDS_WITH,
                expected = expectedNormalized,
            )
        )

        val v = context.get(field) as? PreparedText
        if (v == null) {
            trace?.exit(result = false)
            return false
        }

        val res = if (ignoreCase) {
            v.normalized.endsWith(suffix = expectedNormalized, ignoreCase = true)
        } else {
            v.normalized.endsWith(suffix = expectedNormalized)
        }

        trace?.exit(result = res)
        return res
    }
}

