package ruleengine.dsl.ast

data class ListLiteral(
    val items: List<LiteralAst>
) : LiteralAst
