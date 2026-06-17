package ruleengine.evaluator.context.dto

data class PreparedStringSet(
    val original: Set<String>,
    val normalized: Set<String>
) : PreparedValue
