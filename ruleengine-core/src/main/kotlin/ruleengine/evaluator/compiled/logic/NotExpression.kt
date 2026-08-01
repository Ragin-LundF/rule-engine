package ruleengine.evaluator.compiled.logic

import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

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

