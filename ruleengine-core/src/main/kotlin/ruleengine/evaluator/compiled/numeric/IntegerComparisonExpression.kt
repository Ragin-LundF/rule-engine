package ruleengine.evaluator.compiled.numeric

import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedInteger
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

class IntegerComparisonExpression(
    private val field: FieldId,
    private val expected: Long,
    private val op: IntegerComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = op.name, expected = expected))

        val v = context.get(field) as? PreparedInteger
        if (v == null) {
            trace?.exit(result = false)
            return false
        }

        val res = when (op) {
            IntegerComparisonOperator.EQ -> v.value == expected
            IntegerComparisonOperator.GT -> v.value > expected
            IntegerComparisonOperator.GTE -> v.value >= expected
            IntegerComparisonOperator.LT -> v.value < expected
            IntegerComparisonOperator.LTE -> v.value <= expected
        }

        trace?.exit(result = res)
        return res
    }
}

