package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
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
        val totals = LinkedHashMap<Any, BigDecimal>()
        for (source in sources) {
            for (element in source.collection.resolveRawList(context = context)) {
                accumulate(element = element, source = source, totals = totals)
            }
        }
        return ArrayExpressionValue(
            values = totals.values.map { total -> NumberExpressionValue(value = total) }
        )
    }

    /**
     * Adds one element's value to its key's running total.
     *
     * An element that is not a structure, one carrying no key, and one carrying no number are all
     * skipped rather than reported: none of them can be aligned with anything, and a source that
     * simply does not mention a key is what an outer join is for.
     */
    private fun accumulate(element: Any?, source: Source, totals: MutableMap<Any, BigDecimal>) {
        if (element !is Map<*, *>) {
            return
        }
        val keyValue = element[key] ?: return
        val amount = numberOf(raw = element[source.valueMember]) ?: return
        totals[keyValue] = totals.getOrDefault(keyValue, BigDecimal.ZERO).add(amount)
    }

    private fun numberOf(raw: Any?): BigDecimal? {
        return when (raw) {
            is BigDecimal -> raw
            is Int, is Long, is Short, is Byte -> BigDecimal.valueOf((raw as Number).toLong())
            is Number -> BigDecimal(raw.toString())
            is String -> raw.toBigDecimalOrNull()
            else -> null
        }
    }
}
