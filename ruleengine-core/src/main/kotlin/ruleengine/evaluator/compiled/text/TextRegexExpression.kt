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

        // Out of time means not tested, so not decided: `not ... regex` must not turn into a pass.
        val matched = RegexDeadline.runOrNull(input = fieldValue.original) { input ->
            pattern.containsMatchIn(input = input)
        }
        val verdict = matched?.let { ConditionVerdict.of(value = it) } ?: ConditionVerdict.UNKNOWN
        trace?.exit(verdict = verdict)
        return verdict
    }
}

