package ruleengine.evaluator.compiled.value.result

/**
 * Converts an [ExpressionValue] back to a plain Kotlin value for consumers outside the evaluator —
 * action arguments and `EvaluationResult.variables` — and compares two of them by value.
 *
 * [MissingExpressionValue] and [ObjectExpressionValue] both become `null`: neither carries a value a
 * caller could use, and an action argument that could not be resolved is already modelled as `null`
 * by the rest of the pipeline.
 */
object ExpressionValues {
    fun unwrap(value: ExpressionValue): Any? {
        return when (value) {
            is NumberExpressionValue -> value.value
            is TextExpressionValue -> value.value
            is BooleanExpressionValue -> value.value
            is ArrayExpressionValue -> value.values.map { element -> unwrap(value = element) }
            MissingExpressionValue, ObjectExpressionValue -> null
        }
    }

    /**
     * Value equality of two scalars, matching what `==` does in a comparison.
     *
     * Numbers compare by [java.math.BigDecimal.compareTo] rather than `equals`, so a list holding `1`
     * finds `1.0` — the same rule `ComparisonCompiledExpression` applies to `==`, and the reason
     * membership and the `add` clause's de-duplication agree on what "already present" means.
     *
     * A missing value equals nothing, not even another missing value; a list and a structure have no
     * scalar identity, so they equal nothing either.
     */
    fun equalsByValue(left: ExpressionValue, right: ExpressionValue): Boolean {
        return when {
            left is NumberExpressionValue && right is NumberExpressionValue ->
                left.value.compareTo(right.value) == 0

            left is TextExpressionValue && right is TextExpressionValue -> left.value == right.value
            left is BooleanExpressionValue && right is BooleanExpressionValue -> left.value == right.value
            else -> false
        }
    }

    /** True when [container] already holds a value equal to [element] under [equalsByValue]. */
    fun arrayContains(container: ArrayExpressionValue, element: ExpressionValue): Boolean {
        // ponytail: linear scan. The list holds what one record actually accumulated — a handful of
        // values — so it stays far cheaper than the text matching it guards. If a record ever
        // accumulates hundreds of values, back ArrayExpressionValue with a LinkedHashSet.
        return container.values.any { value -> equalsByValue(left = value, right = element) }
    }
}
