package ruleengine.evaluator.trace.dto

data class NodeMeta(
    val type: NodeType,
    val field: String? = null,
    val operator: String? = null,
    val expected: Any? = null,
    val ruleId: String? = null
)
