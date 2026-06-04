package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.TraceCollector

class TextRegexExpression(
    private val field: FieldId,
    private val pattern: Regex
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = "regex",
                expected = pattern.pattern
            )
        )

        val fieldValue = context.get(field = field) as? ruleengine.evaluator.context.PreparedText
        if (fieldValue == null) {
            trace?.exit(result = false)
            return false
        }

        val res = pattern.containsMatchIn(input = fieldValue.original)
        trace?.exit(result = res)
        return res
    }
}

