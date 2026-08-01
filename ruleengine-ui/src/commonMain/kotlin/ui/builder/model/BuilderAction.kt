package ui.builder.model

/**
 * A single action row rendered in the THEN block.
 */
data class BuilderAction(
    val id: String,
    val name: String,
    val arguments: List<String>,
)
