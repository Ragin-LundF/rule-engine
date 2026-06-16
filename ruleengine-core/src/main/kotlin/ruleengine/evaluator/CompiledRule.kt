package ruleengine.evaluator

import ruleengine.evaluator.compiled.CompiledExpression

data class CompiledRule(
    val id: String,
    val expression: CompiledExpression,
    val actions: List<CompiledAction> = emptyList()
)

