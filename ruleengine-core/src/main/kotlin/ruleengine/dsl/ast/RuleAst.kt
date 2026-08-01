package ruleengine.dsl.ast

data class RuleAst(
    val id: String,
    val description: String? = null,
    val condition: ExpressionAst,
    val actions: List<ActionAst>,
    /**
     * `set` clauses of the `then` block, in source order. They are applied before [actions] resolve
     * and only when the rule matches.
     */
    val assignments: List<VariableAssignmentAst> = emptyList(),
    /**
     * Where the `rule` keyword sits in the source, 1-based, or null when the node was not built by
     * the parser.
     */
    val line: Int? = null,
    val column: Int? = null,
) {

    /** Position is metadata, not identity — see [ConditionAst.equals]. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuleAst) return false

        return id == other.id &&
                description == other.description &&
                condition == other.condition &&
                actions == other.actions &&
                assignments == other.assignments
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + condition.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + assignments.hashCode()
        return result
    }
}
