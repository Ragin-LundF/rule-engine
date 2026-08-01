package ruleengine.evaluator.trace.dto

data class DecisionNode(
    val id: String,
    val type: NodeType,
    val field: String? = null,
    val operator: String? = null,
    val expected: Any? = null,
    /** The value actually found, when the emitting expression knows it. Omitted from JSON when null. */
    val actual: Any? = null,
    val result: Boolean,
    val evaluationTimeMs: Long? = null,
    val ruleId: String? = null,
    val children: List<DecisionNode> = emptyList()
)
