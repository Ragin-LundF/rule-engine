package ruleengine.evaluator.compiled.value.result

/**
 * Converts an [ExpressionValue] back to a plain Kotlin value for consumers outside the evaluator —
 * action arguments and `EvaluationResult.variables`.
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
}
