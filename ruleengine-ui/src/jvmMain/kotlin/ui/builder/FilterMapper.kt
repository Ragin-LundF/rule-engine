package ui.builder

import ruleengine.dsl.ast.ComparisonExpressionAst
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ExpressionAst
import ruleengine.dsl.ast.ValueExpressionRenderer
import ui.builder.model.BuilderFilter
import ui.builder.model.pathOperand

// The `[...]` half of the value-expression mapping, split from `ValueExpressionMapper` so neither
// file has to be read whole to follow the other. A filter's shape is its own subject: which of the
// two spellings the parser chose, and how a legacy predicate's flat field name becomes an operand.

/**
 * Maps a filter expression. Only a single comparison is representable, not an `and`/`or` tree.
 *
 * Both sides go through [mapValueExpression], so a filter may hold whatever a comparison row may
 * hold — an aggregate (`orders[count(items) > 2]`), arithmetic (`orders[total * 2 > 100]`), a
 * further filtered path (`orders[items[paid == true].total > 2]`), a call, a dotted path into the
 * element (`parcels[origin.hub == "HAM"]`), a written-out list, or the name of a field or variable.
 *
 * The engine resolves those names against the element with the document behind it, which is what
 * `OperandRules.filterCatalog` mirrors for the editor.
 */
internal fun mapFilter(expr: ExpressionAst): BuilderFilter? = when (expr) {
    is ComparisonExpressionAst -> {
        val left = mapValueExpression(expr = expr.left)
        val right = mapValueExpression(expr = expr.right)
        if (left == null || right == null) {
            null
        } else {
            BuilderFilter(
                left = left,
                operator = ValueExpressionRenderer.symbol(operator = expr.operator),
                right = right,
            )
        }
    }

    // The legacy spelling, which the parser produces for every operator that does not force the
    // modern path. Its left side is a flat field string rather than a path AST, so it becomes a path
    // operand here — the same shape the modern form arrives in.
    is ConditionAst -> literalToOperand(lit = expr.value)?.let { right ->
        BuilderFilter(
            left = pathOperand(dotted = expr.field),
            operator = RuleAstToBuilderMapper.normalizeOperator(operator = expr.operator),
            right = right,
        )
    }

    else -> null
}
