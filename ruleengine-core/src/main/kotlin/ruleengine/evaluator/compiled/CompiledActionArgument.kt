package ruleengine.evaluator.compiled

import ruleengine.evaluator.context.PreparedRuleContext

/**
 * Sealed hierarchy for the arguments of a compiled action.
 *
 * - [Static] – the value was fully resolved at compile time.
 * - [ExtractionRef] – the value must be computed at evaluation time
 *   by executing a [RegexExtractExpression].
 */
sealed interface CompiledActionArgument {

    /** A pre-resolved, static action argument value. */
    data class Static(val value: Any?) : CompiledActionArgument

    /**
     * An argument whose value is the result of running [extraction] against
     * the current [PreparedRuleContext].
     */
    data class ExtractionRef(
        val extraction: RegexExtractExpression
    ) : CompiledActionArgument

    /**
     * Resolves the argument against the given evaluation context.
     */
    fun resolve(context: PreparedRuleContext): Any? {
        return when (this) {
            is Static -> value
            is ExtractionRef -> extraction.extract(context = context)
        }
    }
}

