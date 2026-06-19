package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext

interface CompiledValueExpression {
    val cost: EvaluationCost

    fun evaluate(context: PreparedRuleContext): ExpressionValue
}
