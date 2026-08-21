package ruleengine.evaluator.compiled.numeric

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedDecimal
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import java.math.BigDecimal

class DecimalComparisonExpression(
    private val field: FieldId,
    private val expected: BigDecimal,
    private val op: ComparisonOperator
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
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
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val res = when (op) {
            ComparisonOperator.EQ -> v.value.compareTo(expected) == 0
            ComparisonOperator.GT -> v.value > expected
            ComparisonOperator.GTE -> v.value >= expected
            ComparisonOperator.LT -> v.value < expected
            ComparisonOperator.LTE -> v.value <= expected
        }

        val verdict = ConditionVerdict.of(value = res)

        trace?.exit(verdict = verdict)

        return verdict
    }
}

