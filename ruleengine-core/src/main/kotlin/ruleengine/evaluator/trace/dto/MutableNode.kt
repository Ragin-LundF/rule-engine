package ruleengine.evaluator.trace.dto

data class MutableNode(
    val id: String,
    val type: NodeType,
    val field: String?,
    val operator: String?,
    val expected: Any?,
    var result: Boolean? = null,
    var startNs: Long? = null,
    var elapsedMs: Long? = null,
    val children: MutableList<MutableNode> = mutableListOf(),
    var ruleId: String? = null
)
