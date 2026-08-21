package ruleengine.evaluator.compiled.text

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
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

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = OperatorNames.REGEX,
                expected = pattern.pattern
            )
        )

        val fieldValue = context.get(field = field) as? PreparedText
        if (fieldValue == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        if (fieldValue.original.length > MAX_INPUT_LENGTH) {
            trace?.exit(verdict = ConditionVerdict.FALSE)
            return ConditionVerdict.FALSE
        }

        val res = pattern.containsMatchIn(input = fieldValue.original)
        val verdict = ConditionVerdict.of(value = res)
        trace?.exit(verdict = verdict)
        return verdict
    }
}

