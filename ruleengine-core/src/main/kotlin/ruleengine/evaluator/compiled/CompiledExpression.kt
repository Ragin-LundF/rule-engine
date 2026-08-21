package ruleengine.evaluator.compiled

import ruleengine.core.domain.dto.ConditionVerdict
import ruleengine.evaluator.context.PreparedRuleContext
import ruleengine.evaluator.trace.TraceCollector

/** One compiled boolean test, ready to run against a prepared context. */
interface CompiledExpression {
    val cost: EvaluationCost

    /**
     * Runs the test and says what it answered.
     *
     * Three-valued, not boolean: a test whose data the record does not carry returns
     * [ConditionVerdict.UNKNOWN] rather than folding that case into "no". Only a leaf and a comparison
     * produce it; the combinators propagate it by Kleene's rules, and a rule with no `not_exists`
     * branch collapses it to false, which is what every rule did before the branch existed.
     */
    fun evaluate(
        context: PreparedRuleContext,
        trace: TraceCollector? = null
    ): ConditionVerdict
}
