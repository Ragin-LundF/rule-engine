package ruleengine.evaluator.trace.dto

data class DecisionTree(
    val root: DecisionNode?,
    val matchedRules: List<String> = emptyList()
)
