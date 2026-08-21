package ruleengine.evaluator.trace

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.evaluator.trace.dto.DecisionNode
import ruleengine.evaluator.trace.dto.NodeMeta

interface TraceCollector {
    fun enter(meta: NodeMeta)

    /**
     * Closes the node opened by the last [enter] with what it answered.
     *
     * A verdict rather than a boolean, so a node that could not be decided is not recorded as "did not
     * hold" — which is the difference between "the amount was below the threshold" and "the record
     * carries no amount", and the one thing someone reading a trace to explain a `not_exists` needs.
     *
     * [branch] is set only on a rule's own node, where it names the block the verdict selected. It is
     * not derivable from the verdict: an undecided condition takes `not_exists` in a rule that declares
     * the block and `else` in one that does not.
     */
    fun exit(verdict: ConditionVerdict, branch: RuleBranch? = null)

    fun root(): DecisionNode?
}
