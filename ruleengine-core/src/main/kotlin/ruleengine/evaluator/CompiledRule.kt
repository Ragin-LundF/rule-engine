package ruleengine.evaluator

import ruleengine.evaluator.compiled.CompiledExpression

data class CompiledRule(
    val id: String,
    val expression: CompiledExpression,
    val actions: List<CompiledAction> = emptyList(),
    /** `set` clauses, applied before [actions] resolve and only when the rule matches. */
    val assignments: List<CompiledAssignment> = emptyList(),
    /** Actions of the optional `else` block, resolved when [expression] is false. */
    val elseActions: List<CompiledAction> = emptyList(),
    /** `set` clauses of the `else` block, applied before [elseActions] resolve. */
    val elseAssignments: List<CompiledAssignment> = emptyList(),
    /** True when a matching rule ends the run: the rules after it are not evaluated. */
    val stopOnThen: Boolean = false,
    /** True when a non-matching rule ends the run through its `else` block. */
    val stopOnElse: Boolean = false,
    /** Actions of the optional `not_exists` block, resolved when [expression] could not be decided. */
    val notExistsActions: List<CompiledAction> = emptyList(),
    /** `set` clauses of the `not_exists` block, applied before [notExistsActions] resolve. */
    val notExistsAssignments: List<CompiledAssignment> = emptyList(),
    /** True when a rule whose condition could not be decided ends the run through that block. */
    val stopOnNotExists: Boolean = false,
) {

    /** True when the rule produces something on a false condition. */
    val hasElseBranch: Boolean
        get() = elseActions.isNotEmpty() || elseAssignments.isNotEmpty() || stopOnElse

    /**
     * True when the rule produces something on a condition it could not decide.
     *
     * Also what decides whether the verdict is read three-valued at all: without the branch an
     * undecided condition is false, and the rule behaves as it did before the branch existed.
     */
    val hasNotExistsBranch: Boolean
        get() = notExistsActions.isNotEmpty() || notExistsAssignments.isNotEmpty() || stopOnNotExists

    /** True when any branch can end the run. */
    val hasStop: Boolean
        get() = stopOnThen || stopOnElse || stopOnNotExists
}

