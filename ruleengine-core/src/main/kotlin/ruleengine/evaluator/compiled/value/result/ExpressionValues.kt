package ruleengine.evaluator.compiled.value.result

import java.time.LocalDate

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
            // ISO-8601 text rather than a `LocalDate`, so a date read into a variable or an action
            // argument serialises the same way it did before dates reached the value path at all.
            is DateExpressionValue -> value.value.toString()
            // A structure stays `null` even though it now carries its element. Handing the map out
            // here would change the shape of `EvaluationResult.variables` and of every action
            // argument built from `set x = customer`, which consumers already read as null.
            is ObjectExpressionValue, MissingExpressionValue -> null
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
            // Placed after the text pair, so two strings still compare as text. A date matched
            // against text only reaches here when one side really is a date, and then the ISO
            // reading is the only one that could have been meant.
            left is DateExpressionValue || right is DateExpressionValue -> {
                val leftDate = asDate(value = left)
                leftDate != null && leftDate == asDate(value = right)
            }

            else -> false
        }
    }

    /**
     * [value] read as a calendar date, or null when it is not one.
     *
     * Text is accepted in ISO-8601 only. A collection member carries no [ruleengine.core.domain.dto.
     * field.FieldDefinition], so a date inside one arrives as the string the input held and there is
     * no declared `format` to read it with — ISO is the one spelling that needs no declaration.
     */
    fun asDate(value: ExpressionValue): LocalDate? {
        return when (value) {
            is DateExpressionValue -> value.value
            is TextExpressionValue -> runCatching { LocalDate.parse(value.value) }.getOrNull()
            else -> null
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
