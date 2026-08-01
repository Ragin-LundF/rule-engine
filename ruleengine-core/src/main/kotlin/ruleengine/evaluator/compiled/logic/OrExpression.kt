package ruleengine.evaluator.compiled.logic

import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

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

