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
