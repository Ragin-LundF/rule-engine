package ruleengine.dsl.ast

data class FieldAccessAst(
    val path: List<PathSegmentAst>
) : ValueExpressionAst
