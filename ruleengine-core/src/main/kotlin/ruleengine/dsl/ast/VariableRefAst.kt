package ruleengine.dsl.ast

/**
 * Reads a variable published by an earlier rule of the same manifest entry, written `$name` in the
 * rule DSL (see [VariableAssignmentAst] for the writing side).
 *
 * Usable anywhere a value expression is: as either side of a comparison, as an operand of an
 * arithmetic expression, and as the argument of an aggregate function.
 */
data class VariableRefAst(val name: String) : ValueExpressionAst
