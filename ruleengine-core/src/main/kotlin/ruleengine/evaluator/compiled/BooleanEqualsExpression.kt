package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedBoolean
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * Compares a boolean field to a `true` / `false` literal.
 *
 * A field that is absent or holds a non-boolean value yields `false`, matching every other
 * comparison node.
 */
class BooleanEqualsExpression(
    private val field: FieldId,
    private val expected: Boolean
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = "EQ", expected = expected))

        val value = context.get(field) as? PreparedBoolean
        if (value == null) {
            trace?.exit(result = false)
            return false
        }

        val result = value.value == expected
        trace?.exit(result = result)
        return result
    }
}
