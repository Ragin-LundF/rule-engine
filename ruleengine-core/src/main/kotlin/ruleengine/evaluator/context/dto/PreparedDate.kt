package ruleengine.evaluator.context.dto

import java.time.LocalDate

/**
 * A calendar date. Time-of-day is deliberately dropped when the input carries one, because rule
 * comparisons are date-based. Use a `date_time` field ([PreparedDateTime]) to compare the time as well.
 */
data class PreparedDate(
    override val value: LocalDate
) : PreparedTemporal<LocalDate>
