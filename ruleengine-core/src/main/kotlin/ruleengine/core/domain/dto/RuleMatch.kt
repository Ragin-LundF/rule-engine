package ruleengine.core.domain.dto

data class RuleMatch(
    val ruleId: String,
    val actions: List<RuleAction>
)
