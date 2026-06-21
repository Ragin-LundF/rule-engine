package ui.builder

/**
 * A single condition row rendered in the WHEN block.
 *
 * [joinToPrevious] is the boolean word (`and` or `or`) placed before this condition
 * when rendering DSL text. The first condition in a rule has an empty value.
 */
/**
 * @param id Unique identifier for this node.
 * @param field The schema field name.
 * @param operator The comparison operator.
 * @param value The comparison value (or low bound for `between`).
 * @param valueTo The high bound for `between`.
 * @param listItems Items for list operators (`in`, `containsAny`, `containsAll`).
 * @param joinToPrevious Join word (`and`/`or`) that precedes this condition in the rule.
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
