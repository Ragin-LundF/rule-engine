package ruleengine.evaluator.compiled

import ruleengine.evaluator.compiled.text.RegexExtractExpression
import ruleengine.evaluator.compiled.value.result.ExpressionValues
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * Sealed hierarchy for the arguments of a compiled action.
 *
 * - [Static] – the value was fully resolved at compile time.
 * - [ExtractionRef] – the value must be computed at evaluation time
 *   by executing a [RegexExtractExpression].
 * - [VariableRef] – the value is read from a variable published by a `set` clause.
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
     * An argument that reads the variable [name] (written `$name` in the DSL). Resolves to `null`
     * when no matching rule has assigned it, which is how an unresolvable argument is modelled
     * everywhere else too.
     */
    data class VariableRef(val name: String) : CompiledActionArgument

    /**
     * Resolves the argument against the given evaluation context.
     */
    fun resolve(context: PreparedRuleContext): Any? {
        return when (this) {
            is Static -> value
            is ExtractionRef -> extraction.extract(context = context)
            is VariableRef -> context.variables[name]?.let { value -> ExpressionValues.unwrap(value = value) }
        }
    }
}

