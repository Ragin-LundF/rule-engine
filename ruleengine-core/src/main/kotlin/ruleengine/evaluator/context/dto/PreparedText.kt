package ruleengine.evaluator.context.dto

data class PreparedText(
    val original: String,
    val normalized: String
) : PreparedValue
