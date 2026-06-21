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

    /** A leaf condition row. */
    data class Condition(
        override val nodeId: String,
        val field: String,
        val operator: String,
        val value: String,
        val valueTo: String = "",
        val listItems: List<String> = emptyList(),
        override val joinToPrevious: String = "",
    ) : BuilderConditionNode

    /** A parenthesized group of child nodes. */
    data class Group(
        override val nodeId: String,
        val nodes: List<BuilderConditionNode>,
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
    /** Rule that can be fully rendered as visual blocks. */
    data class Supported(
        val id: String,
        val conditionNodes: List<BuilderConditionNode>,
        val actions: List<BuilderAction>,
    ) : BuilderRule

    /** Rule that contains syntax the Builder cannot safely render. */
    data class Unsupported(val id: String, val reason: String) : BuilderRule

    /** No rule is currently selected. */
    data object None : BuilderRule
}
