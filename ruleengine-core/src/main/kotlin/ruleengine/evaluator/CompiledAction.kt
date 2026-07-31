package ruleengine.evaluator

import ruleengine.core.domain.dto.RuleAction
import ruleengine.evaluator.compiled.CompiledActionArgument
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * A compiled representation of a rule action.
 *
 * Each argument is a [CompiledActionArgument] that is either a pre-resolved
 * static value or an extraction reference that is resolved at evaluation time.
 *
 * Call [resolve] during evaluation to obtain the concrete [RuleAction] whose
 * arguments are fully materialised for the current [PreparedRuleContext].
 */
class CompiledAction(
    val name: String,
    val arguments: List<CompiledActionArgument>
) {
    /**
     * Resolves all arguments against [context] and returns a concrete
     * [RuleAction] ready to be included in the evaluation result.
     */
    fun resolve(context: PreparedRuleContext): RuleAction {
        val resolvedArguments = arguments.map { arg -> arg.resolve(context = context) }
        return RuleAction(name = name, arguments = resolvedArguments)
    }
}

