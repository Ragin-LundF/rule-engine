package ruleengine.core.analysis

import ruleengine.dsl.ast.condition.AndAst
import ruleengine.dsl.ast.condition.ComparisonExpressionAst
import ruleengine.dsl.ast.condition.ConditionAst
import ruleengine.dsl.ast.condition.ExpressionAst
import ruleengine.dsl.ast.condition.NotAst
import ruleengine.dsl.ast.condition.OrAst
import ruleengine.dsl.ast.path.FieldSegmentAst
import ruleengine.dsl.ast.path.FilterSegmentAst
import ruleengine.dsl.ast.path.JoinSegmentAst
import ruleengine.dsl.ast.path.OrEmptySegmentAst
import ruleengine.dsl.ast.path.PathSegmentAst
import ruleengine.dsl.ast.path.SliceSegmentAst
import ruleengine.dsl.ast.path.SortSegmentAst
import ruleengine.dsl.ast.rule.RuleAst
import ruleengine.dsl.ast.value.ArithmeticValueAst
import ruleengine.dsl.ast.value.FieldAccessAst
import ruleengine.dsl.ast.value.FunctionCallValueAst
import ruleengine.dsl.ast.value.LiteralValueAst
import ruleengine.dsl.ast.value.ValueExpressionAst
import ruleengine.dsl.ast.value.VariableRefAst

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

    /**
     * Every field path read by [rule], from its condition and from the `set` expressions of both
     * branches.
     *
     * Assignments count: `set totalWeightKg = sum(parcels.weightKg)` is a read of `parcels.weightKg`
     * as much as a condition would be, and leaving it out would drop the field from the flow view
     * exactly when a rule set moves an aggregate into a variable. Actions read no fields.
     */
    fun fieldsOf(rule: RuleAst): Set<String> {
        val fields = mutableSetOf<String>()
        collectFromExpression(expr = rule.condition, prefix = "", into = fields)
        val assignments = rule.assignments + rule.elseAssignments + rule.notExistsAssignments
        assignments.forEach { assignment ->
            collectFromValue(expr = assignment.expression, prefix = "", into = fields)
        }
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
            // A variable is produced by another rule, not read from the input — see VariableUsage.
            is VariableRefAst -> Unit
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
        val join = expr.path.firstOrNull() as? JoinSegmentAst
        if (join != null) {
            collectFromJoin(join = join, rest = expr.path.drop(n = 1), prefix = prefix, into = into)
            return
        }
        var current = prefix
        expr.path.forEach { segment ->
            when (segment) {
                is FieldSegmentAst -> current = join(prefix = current, segment = segment.name)
                is FilterSegmentAst ->
                    collectFromExpression(expr = segment.expression, prefix = current, into = into)
                // A slice narrows the collection without naming a field, so the dependency is
                // unchanged: `take(orders, 3).total` still reads `orders.total`.
                is SliceSegmentAst -> Unit
                // A sort does name one, and reads it: `sortBy(orders, "total", desc)` depends on
                // `orders.total` even when nothing else in the rule mentions it. Recorded beside
                // the path rather than descending into it, because the sort stays on the collection.
                is SortSegmentAst -> segment.member?.let { member ->
                    into += join(prefix = current, segment = member)
                }
                // Only ever first, and read by `collectFromJoin` before this loop is reached. An
                // orEmpty changes what a missing value reads as, not which field is read.
                is JoinSegmentAst, is OrEmptySegmentAst -> Unit
            }
        }
        if (current.isNotEmpty()) {
            into += current
        }
    }

    /**
     * A join reads each source's value member and, from the same collection, its key. The rows it
     * builds are not input: a member read, a sort or a filter on them names the key or an alias, so
     * only what a filter reads from the document beyond those counts.
     */
    private fun collectFromJoin(
        join: JoinSegmentAst,
        rest: List<PathSegmentAst>,
        prefix: String,
        into: MutableSet<String>
    ) {
        join.sources.forEach { source ->
            collectFromPath(expr = source.path, prefix = prefix, into = into)
            join.key?.let { key ->
                val keyPath = source.path.path.dropLast(n = 1) + FieldSegmentAst(name = key)
                collectFromPath(expr = FieldAccessAst(path = keyPath), prefix = prefix, into = into)
            }
        }
        val rowMembers = (listOfNotNull(join.key) + join.sources.map { source -> source.alias })
            .map { member -> join(prefix = prefix, segment = member) }
            .toSet()
        val filterReads = mutableSetOf<String>()
        rest.filterIsInstance<FilterSegmentAst>().forEach { filter ->
            collectFromExpression(expr = filter.expression, prefix = prefix, into = filterReads)
        }
        into += filterReads - rowMembers
    }

    private fun join(prefix: String, segment: String): String {
        if (prefix.isEmpty()) {
            return segment
        }
        return "$prefix.$segment"
    }
}
