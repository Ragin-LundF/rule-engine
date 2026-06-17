package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.evaluator.trace.TraceCollector

class AndExpression(children: List<CompiledExpression>) : CompiledExpression {
    private val orderedChildren = children.sortedBy { it.cost }
    override val cost: EvaluationCost = orderedChildren.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.AND))
        for (c in orderedChildren) {
            val res = c.evaluate(context, trace)
            if (!res) {
                trace?.exit(false)
                return false
            }
        }

        trace?.exit(true)
        return true
    }
}

