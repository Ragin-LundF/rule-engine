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

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = if (ignoreCase) "${OperatorNames.IN}IgnoreCase" else OperatorNames.IN,
                expected = expectedNormalized,
            )
        )

        val v = context.get(field) as? PreparedText
        if (v == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val key = if (ignoreCase) v.normalized.lowercase() else v.normalized
        val res = key in matchSet

        val verdict = ConditionVerdict.of(value = res)

        trace?.exit(verdict = verdict)

        return verdict
    }
}

