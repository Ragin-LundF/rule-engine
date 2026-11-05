package ruleengine.evaluator.compiled.value.result

import java.time.LocalDate
import java.time.LocalTime

/**
 * A calendar date, with the time of day when it was read from a declared `date_time` field.
 *
 * Two values that both carry a time compare at time precision; as soon as one side is a plain date
 * the comparison is by calendar day, so a date never compares unequal to a timestamp on the same day.
 * Values read from undeclared input (collection members) carry no time, matching `PreparedDate`.
 */
data class DateExpressionValue(
    val value: LocalDate,
    val timeOfDay: LocalTime? = null
) : ExpressionValue
