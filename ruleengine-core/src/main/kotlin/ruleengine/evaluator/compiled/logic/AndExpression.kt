package ruleengine.evaluator.compiled.logic

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * Every child must hold.
 *
 * Children run cheapest first, and a `false` still ends the run: nothing another child could say
 * changes an `and` that already has one. [ConditionVerdict.UNKNOWN] does **not** end it — a later child
 * may still be `false`, which is the decided answer and the one to report — so an `and` that met an
 * unknown keeps going and only settles for unknown once nothing false turned up.
 */
class AndExpression(children: List<CompiledExpression>) : CompiledExpression {
    private val orderedChildren = children.sortedBy { it.cost }
    override val cost: EvaluationCost = orderedChildren.firstOrNull()?.cost ?: EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(NodeMeta(type = NodeType.AND))
        var undecided = false
        for (child in orderedChildren) {
            when (child.evaluate(context, trace)) {
                ConditionVerdict.FALSE -> {
                    trace?.exit(verdict = ConditionVerdict.FALSE)
                    return ConditionVerdict.FALSE
                }

                ConditionVerdict.UNKNOWN -> undecided = true
                ConditionVerdict.TRUE -> Unit
            }
        }

        val verdict = if (undecided) ConditionVerdict.UNKNOWN else ConditionVerdict.TRUE
        trace?.exit(verdict = verdict)
        return verdict
    }
}
