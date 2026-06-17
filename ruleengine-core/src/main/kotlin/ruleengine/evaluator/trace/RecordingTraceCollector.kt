package ruleengine.evaluator.trace

import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.MutableNode
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.evaluator.trace.dto.toDecisionNode

class RecordingTraceCollector : TraceCollector {
    private val stack = ArrayDeque<MutableNode>()
    private var counter = 0
    private val rootRef = MutableNode(
        id = "n0",
        type = NodeType.EVALUATION,
        field = null,
        operator = null,
        expected = null
    )

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
        if (stack.isEmpty()) rootRef.children.add(node)
        else stack.last().children.add(node)
        stack.addLast(element = node)
    }

    @Suppress("MagicNumber")
    override fun exit(result: Boolean) {
        val node = stack.removeLastOrNull() ?: return
        node.result = result
        val start = node.startNs
        if (start != null) node.elapsedMs = (System.nanoTime() - start) / 1_000_000
    }

    override fun root(): DecisionNode {
        return rootRef.toDecisionNode()
    }
}
