package ruleengine.core.analysis

import ruleengine.dsl.ast.ActionAst
import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticValueAst
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
     * Covers both branches: only one of them runs for a given record, but a variable either branch
     * can read is one the rule depends on.
     */
    fun readsOf(rule: RuleAst): Set<String> {
        val names = linkedSetOf<String>()
        collectFromExpression(expr = rule.condition, into = names)
        val assignments = rule.assignments + rule.elseAssignments
        assignments.forEach { assignment -> collectFromValue(expr = assignment.expression, into = names) }
        val actions = rule.actions + rule.elseActions
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
     * A name set by both branches appears once: whichever branch runs, the variable is published.
     */
    fun writesOf(rule: RuleAst): Set<String> {
        val assignments = rule.assignments + rule.elseAssignments
        return assignments.mapTo(destination = linkedSetOf()) { assignment -> assignment.name }
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
