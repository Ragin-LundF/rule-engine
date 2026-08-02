package ruleengine.evaluator

import ruleengine.evaluator.compiled.value.CompiledValueExpression
import ruleengine.evaluator.compiled.value.result.ArrayExpressionValue
import ruleengine.evaluator.compiled.value.result.BooleanExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValue
import ruleengine.evaluator.compiled.value.result.ExpressionValues
import ruleengine.evaluator.compiled.value.result.NumberExpressionValue
import ruleengine.evaluator.compiled.value.result.TextExpressionValue
import ruleengine.evaluator.context.PreparedRuleContext

/**
 * A compiled `add <valueExpression> to <name>` clause: appends to a list variable.
 *
 * Set semantics — a value the list already holds is not added again, so several rules reaching the
 * same conclusion by different evidence produce one entry. "Already holds" is
 * [ExpressionValues.equalsByValue], the same comparison `contains` uses to find it, so the two can
 * never disagree.
 *
 * The list is written back even when nothing was appended, so the variable exists as an empty list
 * rather than reading as missing. That is what lets `EvaluationResult.variables` show that the
 * accumulating rule ran at all.
 */
class CompiledAddAssignment(
    override val name: String,
    private val expression: CompiledValueExpression
) : CompiledAssignment {

    override fun apply(context: PreparedRuleContext) {
        // A non-array value here is unreachable through the loader: `VariableScopeValidator` rejects a
        // name written by both a `set` and an `add`. Starting a fresh list keeps `Compiler` usable on
        // its own without a crash.
        val existing = (context.variables[name] as? ArrayExpressionValue)?.values.orEmpty()
        val value = expression.evaluate(context = context)

        val appended = when {
            !isAppendable(value = value) -> existing
            existing.any { element -> ExpressionValues.equalsByValue(left = element, right = value) } -> existing
            else -> existing + value
        }
        context.variables[name] = ArrayExpressionValue(values = appended)
    }

    /**
     * Only scalars go into a list. A missing value has nothing to add, and a list or a structure has
     * no scalar identity, so neither could be found again by `contains`.
     */
    private fun isAppendable(value: ExpressionValue): Boolean {
        return value is TextExpressionValue || value is NumberExpressionValue || value is BooleanExpressionValue
    }
}
