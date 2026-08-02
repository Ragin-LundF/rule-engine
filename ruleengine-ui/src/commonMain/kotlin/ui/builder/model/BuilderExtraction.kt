package ui.builder.model


/**
 * The `extract <sourceField> regex("<pattern>", <groupIndex>)` prefix an action may carry.
 *
 * Held on the action rather than beside it because that is how the DSL reads it: an extraction has no
 * meaning without the action whose argument it fills, and the `$1` in that argument refers to this
 * capture group.
 *
 * [groupIndex] stays an `Int` — unlike a slice's count, which is text so a half-typed number survives
 * a keystroke — because it comes from a spinner-style field with a floor of 0 rather than free text.
 */
data class BuilderExtraction(
    val sourceField: String,
    val pattern: String,
    val groupIndex: Int,
)
