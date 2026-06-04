package ruleengine.evaluator

import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.core.domain.RuleAction

data class CompiledRule(
    val id: String,
    val expression: CompiledExpression,
    val actions: List<RuleAction> = emptyList()
)

