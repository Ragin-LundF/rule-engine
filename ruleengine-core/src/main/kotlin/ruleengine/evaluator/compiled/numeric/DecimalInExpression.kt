package ruleengine.evaluator.compiled.numeric

import ruleengine.core.domain.OperatorNames
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

/**
 * `field in [1.0, 2.5]` for a `decimal` field.
 *
 * Matched with `compareTo` rather than by set membership, because `BigDecimal.equals` also compares
 * scale — `1` would not find `1.0`, while every other numeric comparison in the engine says they are
 * the same number. A linear scan is what buys that: the list a rule writes out is a handful of values,
 * far cheaper than the mismatch would be to debug.
 */
class DecimalInExpression(
    private val field: FieldId,
    private val expected: Set<BigDecimal>
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

        val value = context.get(field) as? PreparedDecimal
        if (value == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val matched = expected.any { candidate -> candidate.compareTo(value.value) == 0 }
        val verdict = ConditionVerdict.of(value = matched)
        trace?.exit(verdict = verdict)
        return verdict
    }
}
