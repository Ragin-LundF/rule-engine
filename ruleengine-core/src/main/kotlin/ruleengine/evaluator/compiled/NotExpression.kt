package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType
import ruleengine.evaluator.trace.TraceCollector

class NotExpression(private val child: CompiledExpression) : CompiledExpression {
    override val cost: EvaluationCost = child.cost
    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): Boolean {
        trace?.enter(NodeMeta(type = NodeType.NOT))
        val resChild = child.evaluate(context, trace)
        val res = !resChild
        trace?.exit(res)
        return res
    }
}

