package ruleengine.dsl.ast

data class RuleAst(
    val id: String,
    val description: String? = null,
    val condition: ExpressionAst,
    val actions: List<ActionAst>
)
