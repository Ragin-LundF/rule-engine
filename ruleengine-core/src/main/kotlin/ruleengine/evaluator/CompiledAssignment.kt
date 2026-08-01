package ruleengine.evaluator

import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * A compiled `set <name> = <valueExpression>` clause.
 *
 * [apply] is called by [RuleEngine] only when the owning rule matched, before that rule's actions
 * resolve, so an action of the same rule already sees the value.
 */
class CompiledAssignment(
    val name: String,
    val expression: CompiledValueExpression
) {
    fun apply(context: PreparedRuleContext) {
        context.variables[name] = expression.evaluate(context = context)
    }
}
