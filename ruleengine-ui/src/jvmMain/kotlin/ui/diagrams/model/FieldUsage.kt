package ui.diagrams.model

import ruleengine.dsl.ast.AndAst
import ruleengine.dsl.ast.ArithmeticValueAst
import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.FieldAccessAst
import ruleengine.dsl.ast.FieldSegmentAst
import ruleengine.dsl.ast.FilterSegmentAst
import ruleengine.dsl.ast.FunctionCallValueAst
import ruleengine.dsl.ast.LiteralValueAst
import ruleengine.dsl.ast.NotAst
import ruleengine.dsl.ast.OrAst
import ruleengine.dsl.ast.RuleAst
import ruleengine.dsl.ast.ValueExpressionAst

/**
 * Which field paths a rule reads.
 *
 * The engine has no dependency index — nothing precomputes "rule X reads field Y" — so the field
 * flow view derives it by walking the AST.
 *
 * Paths inside a filter are resolved against the collection they filter, which is the only
 * non-obvious part: in `parcels[origin.hub == "HAM"]` the inner condition reads `origin.hub`
 * relative to a parcel, so the dependency is on `parcels.origin.hub`.
 *
 * Every referenced path is returned verbatim, including intermediate collection paths such as the
 * bare `parcels` in `count(parcels[damaged == true])`. Callers that only care about leaves intersect
 * the result with the schema's leaf paths.
 */
object FieldUsage {

    /** Every field path read by [rule]'s condition. Actions read no fields. */
    fun fieldsOf(rule: RuleAst): Set<String> {
        val fields = mutableSetOf<String>()
        collectFromExpression(expr = rule.condition, prefix = "", into = fields)
        return fields
    }

    private fun collectFromExpression(expr: ExpressionAst, prefix: String, into: MutableSet<String>) {
        when (expr) {
            is ConditionAst -> into += join(prefix = prefix, segment = expr.field)
            is ComparisonExpressionAst -> {
                collectFromValue(expr = expr.left, prefix = prefix, into = into)
                collectFromValue(expr = expr.right, prefix = prefix, into = into)
            }

            is NotAst -> collectFromExpression(expr = expr.child, prefix = prefix, into = into)
            is AndAst -> expr.children.forEach { child ->
                collectFromExpression(expr = child, prefix = prefix, into = into)
            }

            is OrAst -> expr.children.forEach { child ->
                collectFromExpression(expr = child, prefix = prefix, into = into)
            }
        }
    }

    private fun collectFromValue(expr: ValueExpressionAst, prefix: String, into: MutableSet<String>) {
        when (expr) {
            is FieldAccessAst -> collectFromPath(expr = expr, prefix = prefix, into = into)
            is LiteralValueAst -> Unit
            is FunctionCallValueAst -> expr.arguments.forEach { argument ->
                collectFromValue(expr = argument, prefix = prefix, into = into)
            }

            is ArithmeticValueAst -> {
                collectFromValue(expr = expr.left, prefix = prefix, into = into)
                collectFromValue(expr = expr.right, prefix = prefix, into = into)
            }
        }
    }

    /**
     * Walks a path left to right, accumulating the dotted prefix the same way
     * `ValueExpressionRenderer.renderPath` does, so a filter sees the path of the collection it binds
     * to. Only the fully walked path is recorded; intermediate prefixes are not, because
     * `parcels.weightKg` is the dependency, not `parcels` as well.
     */
    private fun collectFromPath(expr: FieldAccessAst, prefix: String, into: MutableSet<String>) {
        var current = prefix
        expr.path.forEach { segment ->
            when (segment) {
                is FieldSegmentAst -> current = join(prefix = current, segment = segment.name)
                is FilterSegmentAst ->
                    collectFromExpression(expr = segment.expression, prefix = current, into = into)
            }
        }
        if (current.isNotEmpty()) {
            into += current
        }
    }

    private fun join(prefix: String, segment: String): String {
        if (prefix.isEmpty()) {
            return segment
        }
        return "$prefix.$segment"
    }
}
