package ui.builder

import ui.builder.model.BuilderOperand
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderComparison
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.pathOperand
import ui.builder.model.pathOrNull

/**
 * Which of the DSL's two condition shapes a row takes — **derived from its operands, never chosen.**
 *
 * The engine decides between them by the shape of the operands, not by anything the author declares:
 *
 * - a **plain field path** against a **literal or list** is a simple condition, and that is the only
 *   form the named operators (`contains`, `between`, `in`, …) exist in;
 * - anything computed is a value-expression comparison, which only the symbolic operators
 *   (`==` `!=` `>` `>=` `<` `<=`) can spell.
 *
 * The form is therefore a *consequence*, and this object computes it. What it replaces was a one-way
 * button: `BuilderEditorState.toComparison` had no inverse, was called with a hard-coded `==` so
 * `amount >= 300` silently became `amount == 300`, and dropped `valueTo`, `listItems` and
 * `ignoreCase` on the way. Since the Builder regenerates the whole rule text on every edit, that was
 * data loss in the file rather than a rendering slip.
 */
object RowForm {

    /**
     * Named operators that have no value-expression spelling at all.
     *
     * `equals` is absent on purpose: `==` means the same thing, so it converts silently. Everything
     * here would have to be reinterpreted to survive the move, and `in` / `between` / `containsAny` /
     * `containsAll` additionally carry data — a list, an upper bound — that a comparison has nowhere
     * to put.
     */
    val NAMED_ONLY: List<String> = listOf(
        OperatorOptions.CONTAINS,
        OperatorOptions.STARTS_WITH,
        OperatorOptions.ENDS_WITH,
        OperatorOptions.REGEX,
        OperatorOptions.IN,
        OperatorOptions.BETWEEN,
        OperatorOptions.CONTAINS_ANY,
        OperatorOptions.CONTAINS_ALL,
    )

    /**
     * True when [operand] is a bare dotted name — no filters, ordering or slice, and not a variable.
     *
     * A variable is excluded because `$name` is not a schema field: the engine routes it through the
     * expression path, and a simple condition naming one would be rejected as an unknown field.
     */
    fun isPlainFieldPath(operand: BuilderOperand): Boolean {
        if (operand !is BuilderOperand.FieldRef) {
            return false
        }
        val firstName = operand.path.firstOrNull()?.name ?: return false
        if (OperatorOptions.isVariableId(fieldId = firstName)) {
            return false
        }
        return operand.path.all { step -> step.decorations.isEmpty() }
    }

    /** True when this comparison says something a simple condition could say just as well. */
    fun canBeSimple(comparison: MutableBuilderComparison): Boolean {
        if (!isPlainFieldPath(operand = comparison.left)) {
            return false
        }
        return comparison.right is BuilderOperand.Literal || comparison.right is BuilderOperand.ListLiteral
    }

    /**
     * Why this simple condition cannot become a comparison — or null when it can.
     *
     * Returning the reason, and refusing, is the whole difference between teaching the DSL and quietly
     * turning `country in ["de","at","ch"]` into `country == ""`.
     */
    fun blockedPromotion(condition: MutableBuilderCondition): String? {
        val operator = condition.operator
        if (operator == OperatorOptions.EQUALS || operator in OperatorOptions.COMPARISON_NUMERIC) {
            return null
        }
        if (operator !in NAMED_ONLY) {
            return null
        }
        val carries = when {
            operator == OperatorOptions.BETWEEN -> " and its second bound"
            condition.listItems.isNotEmpty() -> " and its list of values"
            else -> ""
        }
        return "'$operator' only exists as a named operator on a plain field$carries — a computed side " +
            "needs one of ${OperatorOptions.COMPARISON_NUMERIC.joinToString(separator = " ")}. " +
            "Change the operator first."
    }

    /**
     * Rewrites a comparison as the simple condition it is equivalent to.
     *
     * Nothing is lost: every part of a comparison has a place on a condition, including a list right
     * side and both modifiers.
     */
    fun toSimple(comparison: MutableBuilderComparison): MutableBuilderCondition {
        val right = comparison.right
        val listItems = (right as? BuilderOperand.ListLiteral)?.items ?: emptyList()
        val value = (right as? BuilderOperand.Literal)?.text ?: ""
        return MutableBuilderCondition(
            id = comparison.id,
            field = OperandText.toDsl(operand = comparison.left),
            operator = comparison.operator,
            value = value,
            listItems = listItems,
            ignoreCase = comparison.ignoreCase,
            negated = comparison.negated,
            joinToPrevious = comparison.joinToPrevious,
        )
    }

    /**
     * Rewrites a simple condition as a comparison.
     *
     * Only call this when [blockedPromotion] returns null — otherwise the operator would have to be
     * reinterpreted, which is exactly what the removed button did.
     */
    fun toComparison(condition: MutableBuilderCondition): MutableBuilderComparison {
        val operator = if (condition.operator == OperatorOptions.EQUALS) {
            OperatorOptions.SYMBOL_EQUALS
        } else {
            condition.operator
        }
        return MutableBuilderComparison(
            id = condition.id,
            left = pathOperand(dotted = condition.field),
            operator = operator,
            right = rightOperandOf(condition = condition),
            ignoreCase = condition.ignoreCase,
            negated = condition.negated,
            joinToPrevious = condition.joinToPrevious,
        )
    }

    /**
     * Puts the row [rowId] into whichever form its contents call for, and reports whether it changed.
     *
     * Call after any edit that could change the answer — switching a side's kind, or replacing an
     * operand. This is the same test the DSL parser applies to the text, so the two can never drift.
     *
     * Only demotion happens automatically. Promotion is a deliberate act (the author picked a computed
     * kind for a side), and doing it implicitly would mean guessing at an operator the author has not
     * chosen.
     */
    fun normalizeRow(state: BuilderEditorState, rowId: String): Boolean {
        val node = findNode(nodes = state.conditionNodes, id = rowId) as? MutableConditionNode.ComparisonLeaf
            ?: return false
        val comparison = node.inner
        if (!canBeSimple(comparison = comparison)) {
            return false
        }
        // No type check here, deliberately. A plain field against a literal *is* a simple condition
        // whatever the operator: a symbolic spelling is always writable on a plain field, and a named
        // one can only be there because it was already a condition. Narrowing by the field's declared
        // type would need a catalog this has no business holding, and the validator has the last word.
        val replacement = MutableConditionNode.Leaf(inner = toSimple(comparison = comparison))
        return state.replaceNode(id = rowId, replacement = replacement)
    }

    /**
     * The right side a promoted condition carries over.
     *
     * A list survives as a [BuilderOperand.ListLiteral] so the values are still there if the author
     * changes the operator back; a single value becomes a literal, quoted the way the field's type
     * implies.
     */
    private fun rightOperandOf(condition: MutableBuilderCondition): BuilderOperand {
        if (condition.listItems.isNotEmpty()) {
            return BuilderOperand.ListLiteral(items = condition.listItems.toList())
        }
        val text = condition.value
        return BuilderOperand.Literal(
            text = text,
            numeric = text.trim().toDoubleOrNull() != null,
        )
    }

    /**
     * The row with [id], wherever it sits — a row inside a group is still a row.
     *
     * Kept here rather than reusing the resolver's copy to avoid `ui.builder` depending on
     * `ui.builder.selection` for one traversal; both are three lines over the same tree.
     */
    private fun findNode(nodes: List<MutableConditionNode>, id: String): MutableConditionNode? {
        for (node in nodes) {
            if (node.id == id) {
                return node
            }
            if (node is MutableConditionNode.Group) {
                val found = findNode(nodes = node.nodes, id = id)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    /** True when either side is computed, i.e. the row can only be a comparison. */
    fun hasComputedSide(comparison: MutableBuilderComparison): Boolean {
        return !isPlainFieldPath(operand = comparison.left) ||
            !(comparison.right is BuilderOperand.Literal || comparison.right is BuilderOperand.ListLiteral)
    }

    /** True when the operand is one of the computed kinds, which force the comparison form. */
    fun isComputed(operand: BuilderOperand): Boolean {
        return when (operand) {
            is BuilderOperand.Aggregate, is BuilderOperand.Calc, is BuilderOperand.Call -> true
            is BuilderOperand.FieldRef -> operand.pathOrNull?.any { step -> step.decorations.isNotEmpty() } == true
            else -> false
        }
    }
}
