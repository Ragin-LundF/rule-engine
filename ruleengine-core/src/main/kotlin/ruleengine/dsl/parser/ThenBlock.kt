package ruleengine.dsl.parser

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.VariableAssignmentAst

/** The parsed contents of a `then` block: its actions and its `set` clauses, each in source order. */
internal class ThenBlock(
    val actions: List<ActionAst>,
    val assignments: List<VariableAssignmentAst>,
)
