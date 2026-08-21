package ruleengine.evaluator.compiled.bool

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedBoolean
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * Compares a boolean field to a `true` / `false` literal.
 *
 * A field that is absent or holds a non-boolean value is not decidable and yields
 * [ConditionVerdict.UNKNOWN], matching every other comparison node.
 */
class BooleanEqualsExpression(
    private val field: FieldId,
    private val expected: Boolean
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "EQ", expected = expected))

        val value = context.get(field) as? PreparedBoolean
        if (value == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val result = value.value == expected
        val verdict = ConditionVerdict.of(value = result)
        trace?.exit(verdict = verdict)
        return verdict
    }
}
