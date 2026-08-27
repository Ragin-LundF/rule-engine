package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * `every(collection[predicate])` and `any(collection[predicate])`.
 *
 * Reads [source] as raw elements and runs [predicate] against each of them, stopping at the first
 * element that decides the answer. That short-circuit is why this is a node of its own rather than a
 * comparison of two counts: `any` over a long collection should stop at the first match, and a
 * count-based reading would evaluate the predicate for every element regardless.
 *
 * Empty collections: `every` is true and `any` is false. Both fall out of the loop without a guard,
 * and both are the conventional readings — "every one of nothing satisfies it" is vacuously true,
 * "one of nothing satisfies it" is not.
 *
 * A **missing** collection is a different question and gets a different answer: there is no collection
 * to quantify over, so the result is no value at all and the condition over it is undecided. Reading
 * the source through `resolveRawList` answered `emptyList()` for both, which made a record that never
 * carried the collection indistinguishable from one that carried an empty one — and told a rule with a
 * `not_exists` branch that `every(...)` had been decided.
 *
 * @param requireAll true for `every`, false for `any`.
 */
class CollectionPredicateCompiledValueExpression(
    private val source: FieldAccessCompiledValueExpression,
    private val predicate: CompiledExpression,
    private val requireAll: Boolean
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return context.cache.getOrPut(key = this) { computeValue(context = context) }
    }

    private fun computeValue(context: PreparedRuleContext): ExpressionValue {
        val elements = source.resolveRawListOrNull(context = context) ?: return MissingExpressionValue
        for (element in elements) {
            // An element that is not a structure has no members for the predicate to read, so it
            // cannot satisfy it — the same answer the filter segments give. A predicate that could not
            // be decided does not satisfy it either: `every` and `any` answer about the elements, and
            // an element the predicate could not read is not one that passed it.
            val satisfied = element is Map<*, *> &&
                predicate.evaluate(context = context.child(element = element), trace = null).isTrue()
            if (satisfied != requireAll) {
                return BooleanExpressionValue(value = satisfied)
            }
        }
        return BooleanExpressionValue(value = requireAll)
    }
}
