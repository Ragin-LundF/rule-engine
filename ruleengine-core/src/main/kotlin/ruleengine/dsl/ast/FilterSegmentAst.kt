package ruleengine.dsl.ast

data class FilterSegmentAst(
    val expression: ExpressionAst
) : PathSegmentAst
