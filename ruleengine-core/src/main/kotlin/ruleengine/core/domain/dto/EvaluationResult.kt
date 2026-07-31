package ruleengine.core.domain.dto

data class EvaluationResult(
    val matches: List<RuleMatch>,
    val trace: Any? = null
)
