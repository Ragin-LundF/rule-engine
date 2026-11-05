package ruleengine.dsl.ast.path

import ruleengine.dsl.ast.condition.ExpressionAst

data class FilterSegmentAst(
    val expression: ExpressionAst
) : PathSegmentAst
