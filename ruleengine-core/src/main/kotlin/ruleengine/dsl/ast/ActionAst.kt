package ruleengine.dsl.ast

data class ActionAst(
    val name: String,
    val arguments: List<LiteralAst>
)
