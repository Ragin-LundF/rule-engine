package ruleengine.evaluator.context.dto

import java.time.LocalDate

/**
 * A calendar date. Time-of-day is deliberately dropped when the input carries one, because rule
 * comparisons are date-based.
 */
data class PreparedDate(
    val value: LocalDate
) : PreparedValue
