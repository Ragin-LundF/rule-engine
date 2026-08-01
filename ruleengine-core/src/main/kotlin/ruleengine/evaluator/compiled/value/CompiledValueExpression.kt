package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.context.PreparedRuleContext

interface CompiledValueExpression {
    val cost: EvaluationCost

    fun evaluate(context: PreparedRuleContext): ExpressionValue
}
