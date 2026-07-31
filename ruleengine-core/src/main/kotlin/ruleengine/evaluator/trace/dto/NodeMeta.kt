package ruleengine.evaluator.trace.dto

data class NodeMeta(
    val type: NodeType,
    val field: String? = null,
    val operator: String? = null,
    val expected: Any? = null,
    /**
     * The value actually found, when the emitter knows it. Only meaningful alongside [expected]: a
     * condition that reads `count(...) GT 0` says nothing about why it failed until you can also see
     * that the count was 0.
     */
    val actual: Any? = null,
    val ruleId: String? = null
)
