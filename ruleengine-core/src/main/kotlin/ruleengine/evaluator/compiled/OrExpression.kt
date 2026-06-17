package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.evaluator.trace.TraceCollector

class OrExpression(private val children: List<CompiledExpression>) : CompiledExpression {
    override val cost: EvaluationCost = children.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.OR))
        for (c in children) {
            val res = c.evaluate(context, trace)
            if (res) {
                trace?.exit(true)
                return true
            }
        }

        trace?.exit(false)
        return false
    }
}

