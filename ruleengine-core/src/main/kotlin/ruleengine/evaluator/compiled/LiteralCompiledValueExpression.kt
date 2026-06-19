package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext

class LiteralCompiledValueExpression(
    private val value: ExpressionValue
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return value
    }
}
