package ruleengine.dsl.ast.value

import ruleengine.dsl.ast.literal.LiteralAst

data class LiteralValueAst(
    val literal: LiteralAst
) : ValueExpressionAst
