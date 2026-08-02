package ruleengine.evaluator.compiled.value.result

import java.time.LocalDate

/**
 * A calendar date.
 *
 * Time of day is dropped on the way in, matching `PreparedDate`: the DSL's date arithmetic and
 * comparisons are calendar-day based, so keeping a time would make two values that name the same day
 * compare unequal.
 */
data class DateExpressionValue(
    val value: LocalDate
) : ExpressionValue
