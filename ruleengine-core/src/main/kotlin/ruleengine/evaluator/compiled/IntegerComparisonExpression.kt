package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.TraceCollector

class IntegerComparisonExpression(
    private val field: FieldId,
    private val expected: Long,
    private val op: IntegerComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = op.name, expected = expected))

        val v = context.get(field) as? ruleengine.evaluator.context.PreparedInteger
        if (v == null) {
            trace?.exit(false)
            return false
        }

        val res = when (op) {
            IntegerComparisonOperator.EQ -> v.value == expected
            IntegerComparisonOperator.GT -> v.value > expected
            IntegerComparisonOperator.GTE -> v.value >= expected
            IntegerComparisonOperator.LT -> v.value < expected
            IntegerComparisonOperator.LTE -> v.value <= expected
        }

        trace?.exit(res)
        return res
    }
}

