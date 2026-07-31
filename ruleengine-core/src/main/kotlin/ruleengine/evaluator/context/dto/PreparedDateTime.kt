package ruleengine.evaluator.context.dto

import java.time.LocalDateTime

/**
 * A date with a time of day, compared at the precision the value carries. The engine has no timezone
 * concept, so an `Instant` input is resolved at UTC before it gets here.
 */
data class PreparedDateTime(
    override val value: LocalDateTime
) : PreparedTemporal<LocalDateTime>
