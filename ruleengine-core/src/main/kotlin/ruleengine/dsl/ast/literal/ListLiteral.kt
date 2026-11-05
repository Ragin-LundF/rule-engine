package ruleengine.dsl.ast.literal

data class ListLiteral(
    val items: List<LiteralAst>
) : LiteralAst
