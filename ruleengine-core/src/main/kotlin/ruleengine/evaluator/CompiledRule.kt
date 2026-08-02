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
    val stopOnElse: Boolean = false
) {

    /** True when the rule produces something on a false condition. */
    val hasElseBranch: Boolean
        get() = elseActions.isNotEmpty() || elseAssignments.isNotEmpty() || stopOnElse

    /** True when either branch can end the run. */
    val hasStop: Boolean
        get() = stopOnThen || stopOnElse
}

