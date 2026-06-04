package ruleengine.evaluator.trace

import ruleengine.jackson.JacksonUtil

enum class NodeType { CONDITION, AND, OR, NOT, RULE }

data class DecisionNode(
    val id: String,
    val type: NodeType,
    val field: String? = null,
    val operator: String? = null,
    val expected: Any? = null,
    val result: Boolean,
    val evaluationTimeMs: Long? = null,
    val ruleId: String? = null,
    val children: List<DecisionNode> = emptyList()
)

data class DecisionTree(
    val root: DecisionNode?,
    val matchedRules: List<String> = emptyList()
)

data class NodeMeta(
    val type: NodeType,
    val field: String? = null,
    val operator: String? = null,
    val expected: Any? = null,
    val ruleId: String? = null
)

interface TraceCollector {
    fun enter(meta: NodeMeta)
    fun exit(result: Boolean)
    fun root(): DecisionNode?
}

class RecordingTraceCollector : TraceCollector {
    private data class MutableNode(
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

    private val stack = ArrayDeque<MutableNode>()
    private var counter = 0
    private var rootRef: MutableNode? = null

    override fun enter(meta: NodeMeta) {
        val node = MutableNode(
            id = "n${++counter}",
            type = meta.type,
            field = meta.field,
            operator = meta.operator,
            expected = meta.expected
        )
        node.startNs = System.nanoTime()
        node.ruleId = meta.ruleId
        if (stack.isEmpty()) rootRef = node
        else stack.last().children.add(node)
        stack.addLast(node)
    }

    override fun exit(result: Boolean) {
        val node = stack.removeLastOrNull() ?: return
        node.result = result
        val start = node.startNs
        if (start != null) node.elapsedMs = (System.nanoTime() - start) / 1_000_000
    }

    override fun root(): DecisionNode? {
        return rootRef?.toDecisionNode()
    }

    private fun MutableNode.toDecisionNode(): DecisionNode = DecisionNode(
        id = id,
        type = type,
        field = field,
        operator = operator,
        expected = expected,
        result = result ?: false,
        evaluationTimeMs = elapsedMs,
        ruleId = ruleId,
        children = children.map { it.toDecisionNode() }
    )
}

class NoopTraceCollector : TraceCollector {
    override fun enter(meta: NodeMeta) {}
    override fun exit(result: Boolean) {}
    override fun root(): DecisionNode? = null
}

fun DecisionTree.toJson(): String {
    return JacksonUtil.jsonMapper.writeValueAsString(this)
}

