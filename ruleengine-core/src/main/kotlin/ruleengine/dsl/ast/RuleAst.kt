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
     * Actions of the optional `else` block, in source order. They resolve when [condition] is false
     * instead of [actions], which resolve when it is true. Empty when the rule declares no `else`.
     */
    val elseActions: List<ActionAst> = emptyList(),
    /** `set` clauses of the `else` block. The [assignments] counterpart for the false branch. */
    val elseAssignments: List<VariableAssignmentAst> = emptyList(),
    /**
     * True when the `then` block ends in `stop`: once this branch has fired, the rules declared after
     * this one are not evaluated.
     */
    val stopOnThen: Boolean = false,
    /** True when the `else` block ends in `stop`. The [stopOnThen] counterpart for the false branch. */
    val stopOnElse: Boolean = false,
    /**
     * Actions of the optional `not_exists` block, in source order. They resolve when [condition] could
     * not be decided because the data it reads is missing, instead of [actions] or [elseActions]. Empty
     * when the rule declares no `not_exists`, which is what makes an undecided condition fall back to
     * the false branch as it always did.
     */
    val notExistsActions: List<ActionAst> = emptyList(),
    /** `set` clauses of the `not_exists` block. The [assignments] counterpart for the undecided branch. */
    val notExistsAssignments: List<VariableAssignmentAst> = emptyList(),
    /** True when the `not_exists` block ends in `stop`. The [stopOnThen] counterpart for that branch. */
    val stopOnNotExists: Boolean = false,
    /**
     * Where the `rule` keyword sits in the source, 1-based, or null when the node was not built by
     * the parser.
     */
    val line: Int? = null,
    val column: Int? = null,
) {

    /**
     * True when the rule declares an `else` block that does something.
     *
     * A bare `stop` counts: "halt the run when this condition does not hold" is a real branch, which is
     * why the parser accepts it as a non-empty block.
     */
    val hasElseBranch: Boolean
        get() = elseActions.isNotEmpty() || elseAssignments.isNotEmpty() || stopOnElse

    /**
     * True when the rule declares a `not_exists` block that does something.
     *
     * Load-bearing beyond reporting: it is what decides whether an undecided condition is answered as
     * such or read as false, and the compiler passes it into every `not` of the condition for the same
     * reason. A rule without the block therefore behaves exactly as it did before the block existed.
     */
    val hasNotExistsBranch: Boolean
        get() = notExistsActions.isNotEmpty() || notExistsAssignments.isNotEmpty() || stopOnNotExists

    /** Position is metadata, not identity — see [ConditionAst.equals]. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuleAst) return false

        return id == other.id &&
                description == other.description &&
                condition == other.condition &&
                actions == other.actions &&
                assignments == other.assignments &&
                elseActions == other.elseActions &&
                elseAssignments == other.elseAssignments &&
                stopOnThen == other.stopOnThen &&
                stopOnElse == other.stopOnElse &&
                notExistsActions == other.notExistsActions &&
                notExistsAssignments == other.notExistsAssignments &&
                stopOnNotExists == other.stopOnNotExists
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + condition.hashCode()
        result = 31 * result + actions.hashCode()
        result = 31 * result + assignments.hashCode()
        result = 31 * result + elseActions.hashCode()
        result = 31 * result + elseAssignments.hashCode()
        result = 31 * result + stopOnThen.hashCode()
        result = 31 * result + stopOnElse.hashCode()
        result = 31 * result + notExistsActions.hashCode()
        result = 31 * result + notExistsAssignments.hashCode()
        result = 31 * result + stopOnNotExists.hashCode()
        return result
    }
}
