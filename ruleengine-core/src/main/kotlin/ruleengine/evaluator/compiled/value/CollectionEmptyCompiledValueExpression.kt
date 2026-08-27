package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * `isEmpty(collection)` — true when the record carries the collection and it holds no elements.
 *
 * Reads [source] as raw elements rather than as a value, which is the whole point of the node.
 * `FieldAccessCompiledValueExpression.collapse` maps an empty selection to
 * `MissingExpressionValue`, the same thing an absent field produces, so a value-level reading could
 * not tell "carries nothing" from "carries an empty list" — and `isAvailable` answers `false` to
 * both. Asking the raw walk keeps the two apart:
 *
 * - root absent → `resolveRawListOrNull` is `null` → **false**, there is no empty collection here
 * - root present, no elements → `emptyList()` → **true**
 * - root present with elements → **false**
 *
 * Answers a real boolean in every case, never
 * [ruleengine.core.domain.dto.ConditionVerdict.UNKNOWN], so a rule can guard on emptiness without the
 * guard itself becoming undecidable — the same contract `isAvailable` keeps.
 */
class CollectionEmptyCompiledValueExpression(
    private val source: FieldAccessCompiledValueExpression
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.MEDIUM

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        val elements = source.resolveRawListOrNull(context = context)
        return BooleanExpressionValue(value = elements != null && elements.isEmpty())
    }
}
