package ui.builder.model

/**
 * A single action row rendered in the THEN block.
 *
 * [extraction] carries the optional `extract … regex(…)` prefix. It has to round-trip like anything
 * else the Builder holds: the generated DSL replaces the rule text in the Code editor, so an
 * extraction the action did not carry would be deleted from the file.
 */
data class BuilderAction(
    val id: String,
    val name: String,
    val arguments: List<String>,
    val extraction: BuilderExtraction? = null,
)
