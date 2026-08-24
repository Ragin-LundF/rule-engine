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

    /**
     * Order of two scalars, the counterpart to [equalsByValue] and what `sortBy` orders elements by.
     *
     * Total, so a list holding more than one kind of value still comes out in a defined order rather
     * than an arbitrary one: values of different kinds are separated by kind — numbers, then dates,
     * then text, then booleans — and ordered naturally within a kind. A date matched against ISO
     * text compares as a date, the same exception [equalsByValue] makes and for the same reason.
     *
     * Only meaningful for values [isOrderable] accepts; anything else compares equal here, and the
     * caller decides where to put it.
     *
     * `ComparisonCompiledExpression` still carries its own comparison for `<` and `>`. The two agree
     * on every pair either of them accepts, and could converge — but that is a change to how
     * comparisons behave, not to how sorting does.
     */
    fun compareByValue(left: ExpressionValue, right: ExpressionValue): Int {
        if (left is DateExpressionValue || right is DateExpressionValue) {
            val leftDate = asDate(value = left)
            val rightDate = asDate(value = right)
            if (leftDate != null && rightDate != null) {
                return leftDate.compareTo(rightDate)
            }
        }
        val byKind = orderRank(value = left).compareTo(orderRank(value = right))
        if (byKind != 0) {
            return byKind
        }
        return when {
            left is NumberExpressionValue && right is NumberExpressionValue -> left.value.compareTo(right.value)
            left is TextExpressionValue && right is TextExpressionValue -> left.value.compareTo(right.value)
            left is BooleanExpressionValue && right is BooleanExpressionValue -> left.value.compareTo(right.value)
            else -> 0
        }
    }

    /**
     * True when [value] has an order of its own.
     *
     * A missing value, a structure and a list do not: there is no answer to "which of these two
     * comes first" that would not be invented. `sortBy` puts them last rather than guessing.
     */
    fun isOrderable(value: ExpressionValue): Boolean {
        return value is NumberExpressionValue ||
            value is DateExpressionValue ||
            value is TextExpressionValue ||
            value is BooleanExpressionValue
    }

    private const val RANK_NUMBER: Int = 0
    private const val RANK_DATE: Int = 1
    private const val RANK_TEXT: Int = 2
    private const val RANK_BOOLEAN: Int = 3

    /** Everything with no order of its own shares the last bucket — see [isOrderable]. */
    private const val RANK_UNORDERABLE: Int = 4

    /** Which kind bucket a value sorts into, and the order those buckets take. */
    private fun orderRank(value: ExpressionValue): Int {
        return when (value) {
            is NumberExpressionValue -> RANK_NUMBER
            is DateExpressionValue -> RANK_DATE
            is TextExpressionValue -> RANK_TEXT
            is BooleanExpressionValue -> RANK_BOOLEAN
            else -> RANK_UNORDERABLE
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
