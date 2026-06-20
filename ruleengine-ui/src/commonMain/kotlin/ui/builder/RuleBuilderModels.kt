package ui.builder

/**
 * A single condition row rendered in the WHEN block.
 *
 * [joinToPrevious] is the boolean word (`and` or `or`) placed before this condition
 * when rendering DSL text. The first condition in a rule has an empty value.
 */
data class BuilderCondition(
    val id: String,
    val field: String,
    val operator: String,
    val value: String,
    /** Second bound for the `between` operator. */
    val valueTo: String = "",
    /** Items for list operators (`in`, `containsAny`, `containsAll`). */
    val listItems: List<String> = emptyList(),
    /** Join word that precedes this condition in a top-to-bottom rule. */
    val joinToPrevious: String = "",
)

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
        val conditions: List<BuilderCondition>,
        val actions: List<BuilderAction>,
    ) : BuilderRule

    /** Rule that contains syntax the Builder cannot safely render. */
    data class Unsupported(val id: String, val reason: String) : BuilderRule

    /** No rule is currently selected. */
    data object None : BuilderRule
}
