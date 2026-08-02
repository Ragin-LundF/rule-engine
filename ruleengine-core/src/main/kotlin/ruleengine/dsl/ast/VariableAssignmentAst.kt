package ruleengine.dsl.ast

/**
 * A `set <name> = <valueExpression>` or `add <valueExpression> to <name>` clause of a `then` block.
 *
 * The assignment runs only when the rule matches, before the rule's own actions are resolved, and
 * publishes [name] to every rule that follows in the entry's rule order. Reading a variable that no
 * matching rule has assigned yields a missing value, which makes the reading comparison `false`.
 *
 * [kind] says which clause wrote it: a `set` replaces whatever the name held, an `add` appends to a
 * list and ignores a value the list already carries. Both kinds share this node and therefore one
 * list per branch, so the two interleave in source order.
 */
data class VariableAssignmentAst(
    val name: String,
    val expression: ValueExpressionAst,
    val kind: AssignmentKindAst = AssignmentKindAst.SET,
    /**
     * Where the `set` or `add` keyword sits in the source, 1-based, or null when not built by the
     * parser.
     */
    val line: Int? = null,
    val column: Int? = null,
) {

    /** Position is metadata, not identity — see [RuleAst.equals]. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VariableAssignmentAst) return false

        return name == other.name && expression == other.expression && kind == other.kind
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + expression.hashCode()
        result = 31 * result + kind.hashCode()
        return result
    }
}
