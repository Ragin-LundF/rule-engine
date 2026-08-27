package ruleengine.evaluator.compiled.numeric

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * `field in [1, 2, 3]` for an `integer` field.
 *
 * The set is built at compile time, so the test is one hash lookup however many values it names —
 * the same shape [ruleengine.evaluator.compiled.text.TextInExpression] has for text.
 */
class IntegerInExpression(
    private val field: FieldId,
    private val expected: Set<Long>
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = OperatorNames.IN,
                expected = expected,
            )
        )

        val value = context.get(field) as? PreparedInteger
        if (value == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val verdict = ConditionVerdict.of(value = value.value in expected)
        trace?.exit(verdict = verdict)
        return verdict
    }
}
