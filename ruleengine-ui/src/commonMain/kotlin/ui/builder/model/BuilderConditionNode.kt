package ui.builder.model

/**
 * A node in the builder condition tree — either a single [Condition] leaf
 * or a [Group] of child nodes that render as `(...)` in DSL text.
 */
sealed interface BuilderConditionNode {
    val nodeId: String
    val joinToPrevious: String

    /** True when the node is wrapped in `not (...)`. */
    val negated: Boolean

    /** A leaf condition row comparing a single field to a literal (`field operator value`). */
    data class Condition(
        override val nodeId: String,
        val field: String,
        val operator: String,
        val value: String,
        val valueTo: String = "",
        val listItems: List<String> = emptyList(),
        val ignoreCase: Boolean = false,
        override val negated: Boolean = false,
        override val joinToPrevious: String = "",
    ) : BuilderConditionNode

    /**
     * A leaf condition row comparing two [BuilderOperand]s with a symbolic operator.
     *
     * This is the shape that carries aggregates, arithmetic and filtered paths. [Condition] stays as
     * the representation for plain field-to-literal rows so simple rules render exactly as before.
     */
    data class Comparison(
        override val nodeId: String,
        val left: BuilderOperand,
        val operator: String,
        val right: BuilderOperand,
        val ignoreCase: Boolean = false,
        override val negated: Boolean = false,
        override val joinToPrevious: String = "",
    ) : BuilderConditionNode

    /** A parenthesized group of child nodes. */
    data class Group(
        override val nodeId: String,
        val nodes: List<BuilderConditionNode>,
        override val negated: Boolean = false,
        override val joinToPrevious: String = "",
    ) : BuilderConditionNode
}
