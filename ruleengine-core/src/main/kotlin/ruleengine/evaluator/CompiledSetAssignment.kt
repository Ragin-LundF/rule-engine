package ruleengine.evaluator

import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * A compiled `set <name> = <valueExpression>` clause: publishes the expression's value, replacing
 * whatever the name held before.
 */
class CompiledSetAssignment(
    override val name: String,
    private val expression: CompiledValueExpression
) : CompiledAssignment {
    override fun apply(context: PreparedRuleContext) {
        context.variables[name] = expression.evaluate(context = context)
    }
}
