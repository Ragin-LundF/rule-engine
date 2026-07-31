package ruleengine.evaluator.context.dto

/**
 * A temporal value, shared by [PreparedDate] and [PreparedDateTime] so one pair of compiled expressions
 * serves both `date` and `date_time` fields.
 */
sealed interface PreparedTemporal<T : Comparable<T>> : PreparedValue {
    val value: T

    /**
     * Compares against [other].
     *
     * Both values always come from the same field, whose declared type picks the implementation and
     * therefore `T` on both sides — so the cast cannot fail. Doing it here keeps it to one place instead
     * of one per compiled expression.
     */
    fun compareWith(other: PreparedTemporal<*>): Int {
        @Suppress("UNCHECKED_CAST")
        return value.compareTo(other.value as T)
    }
}
