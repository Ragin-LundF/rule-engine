package ui.builder.model

/** Top-level model for one rule rendered in Builder mode. */
sealed interface BuilderRule {
    /**
     * Rule that can be fully rendered as visual blocks.
     *
     * [description] carries the rule's optional `description` clause. It has no effect on
     * evaluation, but the Builder must round-trip it: the generated DSL replaces the rule text in
     * the Code editor, so anything the Builder does not carry is deleted from the file.
     */
    data class Supported(
        val id: String,
        val description: String = "",
        val conditionNodes: List<BuilderConditionNode>,
        val actions: List<BuilderAction>,
    ) : BuilderRule

    /** Rule that contains syntax the Builder cannot safely render. */
    data class Unsupported(val id: String, val reason: String) : BuilderRule

    /** No rule is currently selected. */
    data object None : BuilderRule
}
