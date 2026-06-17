package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedText
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

class TextRegexExpression(
    private val field: FieldId,
    private val pattern: Regex
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE

    companion object {
        const val MAX_INPUT_LENGTH = 10_000
    }

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = "regex",
                expected = pattern.pattern
            )
        )

        val fieldValue = context.get(field = field) as? PreparedText
        if (fieldValue == null) {
            trace?.exit(result = false)
            return false
        }

        if (fieldValue.original.length > MAX_INPUT_LENGTH) {
            trace?.exit(result = false)
            return false
        }

        val res = pattern.containsMatchIn(input = fieldValue.original)
        trace?.exit(result = res)
        return res
    }
}

