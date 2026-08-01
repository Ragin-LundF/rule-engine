package ui.builder

/**
 * Represents a leaf node in a builder condition tree. This class encapsulates a single condition
 * with its associated field, operator, value(s), and metadata for rendering and logical grouping.
 *
 * @property id A unique identifier for this condition node.
 * @property field The name of the field on which the condition is applied.
 * @property operator The operator used for the condition (e.g., "equals", "between").
 * @property value The primary value for the condition.
 * @property valueTo An optional secondary value used for range conditions (e.g., "between").
 * @property listItems A list of values used for conditions requiring multiple inputs (e.g., "in").
 * @property joinToPrevious Logical connector to the previous node in the tree (e.g., "AND", "OR").
 */
data class BuilderCondition(
    val id: String,
    val field: String,
    val operator: String,
    val value: String,
    val valueTo: String = "",
    val listItems: List<String> = emptyList(),
    val ignoreCase: Boolean = false,
    override val negated: Boolean = false,
    override val joinToPrevious: String = "",
) : BuilderConditionNode {
    override val nodeId: String get() = id
}

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

/**
 * A single action row rendered in the THEN block.
 */
data class BuilderAction(
    val id: String,
    val name: String,
    val arguments: List<String>,
)

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
