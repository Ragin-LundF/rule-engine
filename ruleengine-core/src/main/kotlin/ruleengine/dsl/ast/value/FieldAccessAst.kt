package ruleengine.dsl.ast.value

import ruleengine.dsl.ast.path.PathSegmentAst

data class FieldAccessAst(
    val path: List<PathSegmentAst>
) : ValueExpressionAst
