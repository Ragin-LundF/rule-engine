package ui.builder

import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.mutable.MutableBuilderComparison
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableConditionNode

/**
 * What is wrong with one condition row, said in the row itself.
 *
 * These are not the core validator's diagnostics. A [ruleengine.core.errors.ValidationDiagnostic]
 * carries a file and a line, and nothing in the Builder's chain — parser AST, mapper, [ui.builder.model.BuilderRule],
 * the editor state — records which line a row came from, so a core diagnostic cannot be attributed to a
 * row without threading that provenance the whole way through. The dock shows those against the file.
 *
 * What is here instead is the set of mistakes the Builder has complete information about on its own:
 * a half-filled row. Those are also the ones worth catching inline, because they are what a row looks
 * like *while it is being written* — the moment before the text is generated and the file is rewritten.
 * Waiting for Validate to report them means reporting them after they are on disk.
 *
 * Deliberately not a severity list. A row either has something missing or it does not, and every case
 * below generates text the parser or the validator rejects, so ranking them would be a distinction
 * without a difference.
 */
object RowIssues {

    /** The reason [node] is incomplete, phrased for the row, or null when it is fine. */
    fun of(node: MutableConditionNode): String? = when (node) {
        is MutableConditionNode.Leaf -> conditionIssue(condition = node.inner)
        is MutableConditionNode.ComparisonLeaf -> comparisonIssue(comparison = node.inner)
        // An empty group renders as `()`, which does not parse. Nested groups report their own rows.
        is MutableConditionNode.Group -> "empty group".takeIf { node.nodes.isEmpty() }
    }

    private fun conditionIssue(condition: MutableBuilderCondition): String? {
        val operator = condition.operator
        return when {
            condition.field.isBlank() -> "pick a field"
            operator.isBlank() -> "pick an operator"

            // `between` needs both bounds. One bound generates `amount between 10`, which the parser
            // reads as a missing token rather than as a bound the author has not typed yet.
            OperatorOptions.isBetween(operator) -> "between needs both bounds".takeIf {
                condition.value.isBlank() || condition.valueTo.isBlank()
            }

            OperatorOptions.isList(operator) -> "$operator needs at least one value".takeIf {
                condition.listItems.isEmpty() || condition.listItems.all { item -> item.isBlank() }
            }

            condition.value.isBlank() -> "enter a value"
            else -> null
        }
    }

    private fun comparisonIssue(comparison: MutableBuilderComparison): String? {
        return when {
            comparison.operator.isBlank() -> "pick an operator"
            else -> operandIssue(operand = comparison.left, side = "left")
                ?: operandIssue(operand = comparison.right, side = "right")
        }
    }

    /**
     * The first thing missing inside [operand], named by the [side] it is on.
     *
     * Recurses into arguments and terms, because the hole that stops a rule generating can be several
     * levels down — an `abs()` whose argument is a `sum()` over no path at all reads as complete from
     * the outside.
     */
    private fun operandIssue(operand: BuilderOperand, side: String): String? = when (operand) {
        is BuilderOperand.FieldRef -> "$side side: pick a field".takeIf {
            isIncomplete(path = operand.path)
        }

        is BuilderOperand.Literal -> "$side side: enter a value".takeIf { operand.text.isBlank() }

        is BuilderOperand.ListLiteral -> "$side side: the list is empty".takeIf {
            operand.items.isEmpty() || operand.items.all { item -> item.isBlank() }
        }

        is BuilderOperand.Aggregate -> aggregateIssue(aggregate = operand, side = side)
        is BuilderOperand.Call -> callIssue(call = operand, side = side)
        is BuilderOperand.Calc -> calcIssue(calc = operand, side = side)
    }

    private fun aggregateIssue(aggregate: BuilderOperand.Aggregate, side: String): String? = when {
        aggregate.function.isBlank() -> "$side side: pick a function"
        isIncomplete(path = aggregate.path) -> "$side side: ${aggregate.function}() needs a path"
        else -> null
    }

    private fun callIssue(call: BuilderOperand.Call, side: String): String? = when {
        call.function.isBlank() -> "$side side: pick a function"
        call.args.isEmpty() -> "$side side: ${call.function}() needs an argument"
        else -> call.args.firstNotNullOfOrNull { arg -> operandIssue(operand = arg, side = side) }
    }

    private fun calcIssue(calc: BuilderOperand.Calc, side: String): String? = when {
        calc.terms.isEmpty() -> "$side side: the calculation is empty"
        else -> calc.terms.firstNotNullOfOrNull { term ->
            operandIssue(operand = term.operand, side = side)
        }
    }

    /** A path with no segments, or with a segment left unnamed, cannot be written to text. */
    private fun isIncomplete(path: List<BuilderPathStep>): Boolean {
        return path.isEmpty() || path.any { step -> step.name.isBlank() }
    }
}
