package ruleengine.dsl.ast

data class LiteralValueAst(
    val literal: LiteralAst
) : ValueExpressionAst
