package ruleengine.evaluator.compiled

import ruleengine.core.domain.FieldId
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.evaluator.trace.TraceCollector
import java.math.BigDecimal

class DecimalComparisonExpression(
    private val field: FieldId,
    private val expected: BigDecimal,
    private val op: ComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = op.name,
                expected = expected
            )
        )

        val v = context.get(field) as? PreparedDecimal
        if (v == null) {
            trace?.exit(false)
            return false
        }

        val res = when (op) {
            ComparisonOperator.EQ -> v.value.compareTo(expected) == 0
            ComparisonOperator.GT -> v.value > expected
            ComparisonOperator.GTE -> v.value >= expected
            ComparisonOperator.LT -> v.value < expected
            ComparisonOperator.LTE -> v.value <= expected
        }

        trace?.exit(result = res)
        return res
    }
}

