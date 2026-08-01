package ruleengine.dsl.ast

/**
 * A `set <name> = <valueExpression>` clause of a `then` block.
 *
 * The assignment runs only when the rule matches, before the rule's own actions are resolved, and
 * publishes [name] to every rule that follows in the entry's rule order. Reading a variable that no
 * matching rule has assigned yields a missing value, which makes the reading comparison `false`.
 */
data class VariableAssignmentAst(
    val name: String,
    val expression: ValueExpressionAst,
    /** Where the `set` keyword sits in the source, 1-based, or null when not built by the parser. */
    val line: Int? = null,
    val column: Int? = null,
) {

    /** Position is metadata, not identity — see [RuleAst.equals]. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VariableAssignmentAst) return false

        return name == other.name && expression == other.expression
    }

    override fun hashCode(): Int {
        return 31 * name.hashCode() + expression.hashCode()
    }
}
