package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext

class LiteralCompiledValueExpression(
    private val value: ExpressionValue
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.VERY_CHEAP

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return value
    }
}
