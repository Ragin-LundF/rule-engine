package ruleengine.dsl.ast.value

import ruleengine.dsl.ast.path.PathSegmentAst

data class FieldAccessAst(
    val path: List<PathSegmentAst>,
    /**
     * Written with the `optional` marker, which is legal only on a `sumByKey` / `joinByKey` source:
     * `optional src.member` reads exactly like `orEmpty(src).member`. Kept as written rather than
     * rewritten into an [ruleengine.dsl.ast.path.OrEmptySegmentAst], so the rule text survives a
     * round trip through the editor.
     */
    val optional: Boolean = false
) : ValueExpressionAst
