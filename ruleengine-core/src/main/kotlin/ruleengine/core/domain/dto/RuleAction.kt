package ruleengine.core.domain.dto

data class RuleAction(
    val name: String,
    val arguments: List<Any?> = emptyList()
)
