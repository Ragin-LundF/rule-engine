package ruleengine.evaluator.trace

import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeMeta

class NoopTraceCollector : TraceCollector {
    override fun enter(meta: NodeMeta) {
        // empty noop
    }
    override fun exit(result: Boolean) {
        // empty noop
    }
    override fun root(): DecisionNode? {
        return null
    }
}
