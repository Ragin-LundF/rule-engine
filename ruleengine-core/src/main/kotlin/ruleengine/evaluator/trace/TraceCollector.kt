package ruleengine.evaluator.trace

import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeMeta

interface TraceCollector {
    fun enter(meta: NodeMeta)
    fun exit(result: Boolean)
    fun root(): DecisionNode?
}
