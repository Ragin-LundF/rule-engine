package ruleengine.core.analysis

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.AssignmentKindAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.LiteralAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.SliceSegmentAst
import ruleengine.dsl.ast.ValueExpressionAst
import ruleengine.dsl.ast.VariableRefAst
import ruleengine.dsl.ast.VariableRefLiteral

/**
 * Which variables a rule reads and which it publishes.
 *
 * The counterpart of [FieldUsage] for the `$name` / `set` pair. Used by the validator to check that
 * every read is preceded by a write, and by the editor to offer the variables that are in scope at a
 * given rule.
 *
 * Names are returned without the `$` prefix.
 */
object VariableUsage {

    /**
     * Variables [rule] reads — in its condition, in its `set` expressions and in its action arguments.
     *
     * Covers every branch: only one of them runs for a given record, but a variable any branch can
     * read is one the rule depends on.
     */
    fun readsOf(rule: RuleAst): Set<String> {
        val names = linkedSetOf<String>()
        collectFromExpression(expr = rule.condition, into = names)
        val assignments = rule.assignments + rule.elseAssignments + rule.notExistsAssignments
        assignments.forEach { assignment -> collectFromValue(expr = assignment.expression, into = names) }
        val actions = rule.actions + rule.elseActions + rule.notExistsActions
        actions.forEach { action ->
            action.arguments.forEach { argument -> collectFromLiteral(literal = argument, into = names) }
        }
        return names
    }

    /**
     * Variables read by a boolean expression — a rule condition or a filter predicate.
     *
     * Separate from [readsOf] because scope checking has to follow the order in which a rule reads:
     * its condition first, then each `set` expression, then its actions.
     */
    fun readsOfExpression(expr: ExpressionAst): Set<String> {
        val names = linkedSetOf<String>()
        collectFromExpression(expr = expr, into = names)
        return names
    }

    /** Variables read by a value expression, such as the right-hand side of a `set` clause. */
    fun readsOfValue(expr: ValueExpressionAst): Set<String> {
        val names = linkedSetOf<String>()
        collectFromValue(expr = expr, into = names)
        return names
    }

    /** Variables read by the arguments of [actions]. */
    fun readsOfActions(actions: List<ActionAst>): Set<String> {
        val names = linkedSetOf<String>()
        actions.forEach { action ->
            action.arguments.forEach { argument -> collectFromLiteral(literal = argument, into = names) }
        }
        return names
    }

    /**
     * Variables [rule] publishes through its `set` clauses, in source order, `then` branch first.
     *
     * A name set by more than one branch appears once: whichever branch runs, the variable is published.
     */
    fun writesOf(rule: RuleAst): Set<String> {
        val assignments = rule.assignments + rule.elseAssignments + rule.notExistsAssignments
        return assignments.mapTo(destination = linkedSetOf()) { assignment -> assignment.name }
    }

    /**
     * Variables [rule] publishes, mapped to the clause that writes each one.
     *
     * The kind is what tells a plain value from an accumulator, and a caller that has to hand the scope
     * of one part of an entry to the validator needs it: without it a `set`/`add` clash across two
     * files could not be reported. The first write of a name wins, matching how the validator records
     * the kind it checks later writes against.
     */
    fun writeKindsOf(rule: RuleAst): Map<String, AssignmentKindAst> {
        val kinds = LinkedHashMap<String, AssignmentKindAst>()
        for (assignment in rule.assignments + rule.elseAssignments + rule.notExistsAssignments) {
            kinds.putIfAbsent(assignment.name, assignment.kind)
        }
        return kinds
    }

    private fun collectFromExpression(expr: ExpressionAst, into: MutableSet<String>) {
        when (expr) {
            // A legacy condition names a field by plain text and cannot hold a variable.
            is ConditionAst -> Unit
            is ComparisonExpressionAst -> {
                collectFromValue(expr = expr.left, into = into)
                collectFromValue(expr = expr.right, into = into)
            }

            is NotAst -> collectFromExpression(expr = expr.child, into = into)
            is AndAst -> expr.children.forEach { child -> collectFromExpression(expr = child, into = into) }
            is OrAst -> expr.children.forEach { child -> collectFromExpression(expr = child, into = into) }
        }
    }

    private fun collectFromValue(expr: ValueExpressionAst, into: MutableSet<String>) {
        when (expr) {
            is VariableRefAst -> into += expr.name
            is LiteralValueAst -> Unit
            is FunctionCallValueAst -> expr.arguments.forEach { argument ->
                collectFromValue(expr = argument, into = into)
            }

            is ArithmeticValueAst -> {
                collectFromValue(expr = expr.left, into = into)
                collectFromValue(expr = expr.right, into = into)
            }

            // A filter predicate is evaluated per element but reads the same variables.
            is FieldAccessAst -> expr.path.forEach { segment ->
                when (segment) {
                    is FilterSegmentAst -> collectFromExpression(expr = segment.expression, into = into)
                    // A slice holds a literal count, so it can read no variable.
                    is FieldSegmentAst, is SliceSegmentAst -> Unit
                }
            }
        }
    }

    private fun collectFromLiteral(literal: LiteralAst, into: MutableSet<String>) {
        when (literal) {
            is VariableRefLiteral -> into += literal.name
            is ListLiteral -> literal.items.forEach { item -> collectFromLiteral(literal = item, into = into) }
            else -> Unit
        }
    }
}
