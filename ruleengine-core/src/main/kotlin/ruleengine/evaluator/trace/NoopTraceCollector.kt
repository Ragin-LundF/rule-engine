package ruleengine.evaluator.trace

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeMeta

class NoopTraceCollector : TraceCollector {
    override fun enter(meta: NodeMeta) {
        // empty noop
    }

    override fun exit(verdict: ConditionVerdict, branch: RuleBranch?) {
        // empty noop
    }

    override fun root(): DecisionNode? {
        return null
    }
}
