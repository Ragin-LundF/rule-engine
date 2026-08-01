package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector

/** One compiled boolean test, ready to run against a prepared context. */
interface CompiledExpression {
    val cost: EvaluationCost
    fun evaluate(
        context: PreparedRuleContext,
        trace: TraceCollector? = null
    ): Boolean
}
