package ruleengine.evaluator.context.dto

import java.time.LocalDate

/**
 * A temporal value, shared by [PreparedDate] and [PreparedDateTime] so one pair of compiled expressions
 * serves both `date` and `date_time` fields.
 */
sealed interface PreparedTemporal<T : Comparable<T>> : PreparedValue {
    val value: T

    /**
     * Compares against [other].
     *
     * Both values come from the same field, whose declared type picks the implementation and therefore
     * `T` on both sides — except a date-only literal on a `date_time` field, compared by calendar day.
     * Doing it here keeps it to one place instead of one per compiled expression.
     */
    fun compareWith(other: PreparedTemporal<*>): Int {
        if (this::class != other::class) {
            // A date-only literal on a `date_time` field (`bookedAt lt "2024-06-16"`): by calendar day.
            return calendarDay(temporal = this).compareTo(calendarDay(temporal = other))
        }
        @Suppress("UNCHECKED_CAST")
        return value.compareTo(other.value as T)
    }

    private fun calendarDay(temporal: PreparedTemporal<*>): LocalDate {
        return when (temporal) {
            is PreparedDate -> temporal.value
            is PreparedDateTime -> temporal.value.toLocalDate()
        }
    }
}
