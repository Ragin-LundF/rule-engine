package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.NodeMeta
import ruleengine.evaluator.trace.NodeType
import ruleengine.evaluator.trace.TraceCollector
import java.math.BigDecimal

class DecimalComparisonExpression(
    private val field: FieldId,
    private val expected: BigDecimal,
    private val op: ComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.CONDITION, field = field.value, operator = op.name, expected = expected))

        val v = context.get(field) as? ruleengine.evaluator.context.PreparedDecimal
        if (v == null) {
            trace?.exit(false)
            return false
        }

        val res = when (op) {
            ComparisonOperator.EQ -> v.value.compareTo(expected) == 0
            ComparisonOperator.GT -> v.value.compareTo(expected) > 0
            ComparisonOperator.GTE -> v.value.compareTo(expected) >= 0
            ComparisonOperator.LT -> v.value.compareTo(expected) < 0
            ComparisonOperator.LTE -> v.value.compareTo(expected) <= 0
        }

        trace?.exit(res)
        return res
    }
}

