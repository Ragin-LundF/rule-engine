package ruleengine.evaluator.compiled.value.function

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.FieldAccessCompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.context.NumericInput
import ruleengine.evaluator.context.PreparedRuleContext
import java.math.BigDecimal

/**
 * `sumByKey("month", salesByMonth.amount, refundsByMonth.amount)` — one total per key, across
 * collections that are otherwise unrelated.
 *
 * The join is an outer one: every key any source mentions appears in the result, and a source that
 * does not mention it contributes zero. Keys come out in first-seen order, reading the sources left
 * to right, so the result is stable enough to compare element by element.
 *
 * Duplicate keys within one source are summed, which makes the function total-preserving: the sum of
 * the result always equals the sum of every value read, whether or not the keys were unique.
 *
 * Each source is read as raw elements rather than as a projected value, because a key and its value
 * live on the same element and a projection has already separated them.
 *
 * A source the record does not carry makes the whole result missing, while an empty source simply
 * contributes nothing.
 */
class KeyedSumCompiledValueExpression(
    private val key: String,
    private val sources: List<Source>,
    override val cost: EvaluationCost = EvaluationCost.EXPENSIVE
) : CompiledValueExpression {

    /** One joined collection: where to read its elements, and which member holds the number. */
    data class Source(
        val collection: FieldAccessCompiledValueExpression,
        val valueMember: String
    )

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return context.cache.getOrPut(key = this) { computeValue(context = context) }
    }

    private fun computeValue(context: PreparedRuleContext): ExpressionValue {
        val perSource = totalsPerSource(key = key, sources = sources, context = context)
            ?: return MissingExpressionValue
        val totals = LinkedHashMap<Any, BigDecimal>()
        for (sourceTotals in perSource) {
            sourceTotals.forEach { (keyValue, amount) -> totals.merge(keyValue, amount, BigDecimal::add) }
        }
        return ArrayExpressionValue(
            values = totals.values.map { total -> NumberExpressionValue(value = total) }
        )
    }

    internal companion object {
        /**
         * One total per key for each source, keys in first-seen order — the summing `sumByKey` and
         * `joinByKey` share, so the two can never disagree on what a key's value is.
         *
         * Null when any source is missing: a total over a collection the record does not carry is not
         * zero, it is unknown. An empty source is fine and simply contributes no keys.
         */
        fun totalsPerSource(
            key: String,
            sources: List<Source>,
            context: PreparedRuleContext
        ): List<Map<Any, BigDecimal>>? {
            return sources.map { source ->
                val elements = source.collection.resolveRawListOrNull(context = context) ?: return null
                totalsOf(elements = elements, key = key, valueMember = source.valueMember)
            }
        }

        private fun totalsOf(elements: List<Any?>, key: String, valueMember: String): Map<Any, BigDecimal> {
            val totals = LinkedHashMap<Any, BigDecimal>()
            for (element in elements) {
                accumulate(element = element, key = key, valueMember = valueMember, totals = totals)
            }
            return totals
        }

        /**
         * Adds one element's value to its key's running total.
         *
         * An element that is not a structure, one carrying no key, and one carrying no number are all
         * skipped rather than reported: none of them can be aligned with anything, and a source that
         * simply does not mention a key is what an outer join is for.
         */
        private fun accumulate(element: Any?, key: String, valueMember: String, totals: MutableMap<Any, BigDecimal>) {
            if (element !is Map<*, *>) {
                return
            }
            val keyValue = element[key] ?: return
            val amount = NumericInput.decimalOrNull(raw = element[valueMember]) ?: return
            totals[keyValue] = totals.getOrDefault(keyValue, BigDecimal.ZERO).add(amount)
        }
    }
}
