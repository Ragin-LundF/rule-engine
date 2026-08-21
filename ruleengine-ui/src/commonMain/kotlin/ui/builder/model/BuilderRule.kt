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
        /** `set` rows of the THEN block, rendered above the actions. */
        val variables: List<BuilderVariable> = emptyList(),
        /** Actions of the ELSE block. Empty when the rule declares no false branch. */
        val elseActions: List<BuilderAction> = emptyList(),
        /** `set` rows of the ELSE block, rendered above its actions. */
        val elseVariables: List<BuilderVariable> = emptyList(),
        /** True when the THEN branch ends the run — rendered as a removable badge, always last. */
        val stopOnThen: Boolean = false,
        /** True when the ELSE branch ends the run. */
        val stopOnElse: Boolean = false,
        /** Actions of the NOT_EXISTS block. Empty when the rule declares no missing-data branch. */
        val notExistsActions: List<BuilderAction> = emptyList(),
        /** `set` rows of the NOT_EXISTS block, rendered above its actions. */
        val notExistsVariables: List<BuilderVariable> = emptyList(),
        /** True when the NOT_EXISTS branch ends the run. */
        val stopOnNotExists: Boolean = false,
    ) : BuilderRule

    /** Rule that contains syntax the Builder cannot safely render. */
    data class Unsupported(val id: String, val reason: String) : BuilderRule

    /** No rule is currently selected. */
    data object None : BuilderRule
}
