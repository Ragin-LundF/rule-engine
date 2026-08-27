package ruleengine.evaluator.compiled.temporal

import ruleengine.core.domain.OperatorNames
import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.field.FieldId
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.context.dto.PreparedTemporal
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * `field in ["2024-01-01", "2024-07-01"]` for a `date` or `date_time` field.
 *
 * The expected values are parsed at compile time under the field's declared `format`, so a rule may
 * write its dates the same way the input does. Matched with `compareWith` rather than by set
 * membership, which is what makes a `date_time` compare on its instant rather than on how it was
 * spelled.
 */
class DateInExpression(
    private val field: FieldId,
    private val expected: Set<PreparedTemporal<*>>
) : CompiledExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(
            meta = NodeMeta(
                type = NodeType.CONDITION,
                field = field.value,
                operator = OperatorNames.IN,
                expected = expected.map { candidate -> candidate.value.toString() },
            )
        )

        val value = context.get(field) as? PreparedTemporal<*>
        if (value == null) {
            // Absent from the record, or present in a shape this test cannot read: not decidable.
            trace?.exit(verdict = ConditionVerdict.UNKNOWN)
            return ConditionVerdict.UNKNOWN
        }

        val matched = expected.any { candidate -> candidate.compareWith(other = value) == 0 }
        val verdict = ConditionVerdict.of(value = matched)
        trace?.exit(verdict = verdict)
        return verdict
    }
}
