package ruleengine.evaluator.compiled.value

import ruleengine.evaluator.compiled.EvaluationCost
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.MissingExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * Reads a variable published by a `set` clause of an earlier matching rule (`$name` in the DSL).
 *
 * A variable no matching rule has assigned reads as [MissingExpressionValue], exactly like an absent
 * input field, which makes the surrounding comparison `false` instead of failing the evaluation.
 * That a variable is assigned by *some* earlier rule is checked at load time by `Validator`.
 */
class VariableRefCompiledValueExpression(
    private val name: String
) : CompiledValueExpression {
    override val cost: EvaluationCost = EvaluationCost.CHEAP

    override fun evaluate(context: PreparedRuleContext): ExpressionValue {
        return context.variables[name] ?: MissingExpressionValue
    }
}
