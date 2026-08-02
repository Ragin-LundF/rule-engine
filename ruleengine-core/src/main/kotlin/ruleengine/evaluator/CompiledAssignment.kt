package ruleengine.evaluator

import ruleengine.evaluator.context.PreparedRuleContext

/**
 * A compiled `set` or `add` clause.
 *
 * [apply] is called by [RuleEngine] only for the branch that fired, before that branch's actions
 * resolve, so an action of the same rule already sees the value.
 */
sealed interface CompiledAssignment {

    /** The variable written, without the `$` prefix. */
    val name: String

    fun apply(context: PreparedRuleContext)
}
