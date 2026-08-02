package ruleengine.evaluator.compiled

import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ExpressionValue

/**
 * Memoizes what a value expression evaluated to, for the lifetime of one context.
 *
 * A cache belongs to exactly one context. `PreparedRuleContext.child` gives each element of a
 * filtered collection its own, because the key is the compiled node and nothing else: one cache
 * shared across elements would serve the first element's aggregate to every element after it.
 */
class EvaluationCache {

    /**
     * Allocated on first use. A context is created per element of every filtered collection, and the
     * overwhelming majority of filter predicates hold no function call at all, so the common case
     * must not pay for a map it never writes to.
     */
    private var values: MutableMap<CompiledValueExpression, ExpressionValue>? = null

    fun getOrPut(key: CompiledValueExpression, compute: () -> ExpressionValue): ExpressionValue {
        val existing = values ?: mutableMapOf<CompiledValueExpression, ExpressionValue>().also { created ->
            values = created
        }
        return existing.getOrPut(key = key, defaultValue = compute)
    }

    /** Drops every memoized value, so re-evaluating the same context recomputes them. */
    fun clear() {
        values?.clear()
    }
}
