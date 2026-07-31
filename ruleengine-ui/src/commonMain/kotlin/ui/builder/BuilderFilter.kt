package ui.builder

/** A single `[field op value]` filter applied to the step it belongs to. */
data class BuilderFilter(
    val field: String,
    val operator: String,
    val value: String,
)
