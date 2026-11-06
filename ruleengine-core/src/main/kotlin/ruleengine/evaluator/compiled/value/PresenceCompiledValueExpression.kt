package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * `isPresent(x)` and `isMissing(x)`: whether the record carries `x`, empty or not.
 *
 * Present is `isAvailable(x) or isEmpty(x)`, so it is built from those two nodes rather than from a
 * third reading of the input. [empty] is null for an argument that is not a path, where present is
 * plain `isAvailable(x)`. A filter cannot make a carried collection missing: it may select nothing,
 * and `isEmpty` answers that case.
 *
 * Like both of its parts it answers a real boolean, never
 * [ruleengine.core.domain.dto.ConditionVerdict.UNKNOWN].
 *
 * @param missing true for `isMissing`, which is always `not isPresent`.
 */
class PresenceCompiledValueExpression(
    private val available: CompiledValueExpression,
    private val empty: CollectionEmptyCompiledValueExpression?,
    private val missing: Boolean
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        val present = isTrue(value = available.evaluate(context = context)) ||
            (empty != null && isTrue(value = empty.evaluate(context = context)))
        return BooleanExpressionValue(value = present != missing)
    }

    private fun isTrue(value: ExpressionValue): Boolean {
        return (value as? BooleanExpressionValue)?.value == true
    }
}
