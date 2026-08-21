package ruleengine.evaluator.compiled.logic

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.evaluator.compiled.CompiledExpression
import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector
import ruleengine.evaluator.trace.dto.NodeMeta
import ruleengine.evaluator.trace.dto.NodeType

/**
 * Negates its child.
 *
 * [unknownAware] is what keeps every rule written before `not_exists` existed behaving as it did, and it
 * is the only place the two- and three-valued readings disagree. `and` and `or` agree once an unknown
 * collapses to false at the top of the rule; `not` cannot, because it happens *before* that collapse:
 * a child with no data used to answer false, so `not` used to answer true. A rule that declares no
 * `not_exists` branch therefore keeps reading it that way, and one that does gets Kleene's
 * `not unknown = unknown`.
 *
 * The concrete case this protects is the guarded accumulator: `not $topics contains "x"` on a list no
 * rule has filled yet has to be *true*, or the first rule of the guarded set never fires.
 */
class NotExpression(
    private val child: CompiledExpression,
    private val unknownAware: Boolean = false,
) : CompiledExpression {
    override val cost: EvaluationCost = child.cost

    override fun evaluate(context: PreparedRuleContext, trace: TraceCollector?): ConditionVerdict {
        trace?.enter(NodeMeta(type = NodeType.NOT))
        val verdict = when (child.evaluate(context, trace)) {
            ConditionVerdict.TRUE -> ConditionVerdict.FALSE
            ConditionVerdict.FALSE -> ConditionVerdict.TRUE
            ConditionVerdict.UNKNOWN -> if (unknownAware) ConditionVerdict.UNKNOWN else ConditionVerdict.TRUE
        }
        trace?.exit(verdict = verdict)
        return verdict
    }
}
