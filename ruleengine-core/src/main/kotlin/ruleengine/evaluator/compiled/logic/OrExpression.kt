package ruleengine.evaluator.compiled.logic

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * At least one child must hold.
 *
 * The mirror image of [AndExpression]: a `true` ends the run, and a child whose data is missing does
 * not — one side of an `or` having no data is no reason to give up on the other. An `or` is unknown only
 * when no child held and at least one could not be decided.
 */
class OrExpression(private val children: List<CompiledExpression>) : CompiledExpression {
    override val cost: EvaluationCost = children.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(NodeMeta(type = NodeType.OR))
        var undecided = false
        for (child in children) {
            when (child.evaluate(context, trace)) {
                ConditionVerdict.TRUE -> {
                    trace?.exit(verdict = ConditionVerdict.TRUE)
                    return ConditionVerdict.TRUE
                }

                ConditionVerdict.UNKNOWN -> undecided = true
                ConditionVerdict.FALSE -> Unit
            }
        }

        val verdict = if (undecided) ConditionVerdict.UNKNOWN else ConditionVerdict.FALSE
        trace?.exit(verdict = verdict)
        return verdict
    }
}
