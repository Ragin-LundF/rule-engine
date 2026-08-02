package ruleengine.dsl.parser

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.VariableAssignmentAst

/**
 * The parsed contents of a `then` or `else` block: its actions and its `set` clauses, each in source
 * order, plus whether the block ends the run.
 */
internal class ThenBlock(
    val actions: List<ActionAst>,
    val assignments: List<VariableAssignmentAst>,
    /**
     * True when the block declares `stop`: the rules after this one are not evaluated once this branch
     * has fired.
     *
     * A flag rather than an entry in [actions], because `stop` is required to be the block's last
     * statement — so there is no position to record, and none that could drift out of place.
     */
    val stop: Boolean = false,
)
