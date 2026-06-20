package ui.builder

/**
 * Generates valid rule DSL text from a [BuilderEditorState].
 *
 * The canonical saved format is always DSL text; this object is the only place
 * that converts Builder state back to text. It must never be called when
 * [BuilderEditorState.isLocked] is true.
 *
 * Output format mirrors the rule DSL spec:
 * ```
 * rule "id" {
 *   when
 *     field operator value
 *     and field operator value
 *   then
 *     action arg
 * }
 * ```
 */
object BuilderToRuleDsl {

    /**
     * Generates DSL text for the given [state].
     * Returns null if the state is locked or has no conditions.
     */
    fun generate(state: BuilderEditorState): String? {
        if (state.isLocked) return null
        if (state.ruleId.isBlank()) return null

        val sb = StringBuilder()
        sb.append("rule \"${state.ruleId}\" {\n")
        sb.append("  when\n")

        state.conditions.forEachIndexed { index, cond ->
            val prefix = if (index == 0) "    " else "    and "
            sb.append("$prefix${renderCondition(cond)}\n")
        }

        sb.append("  then\n")
        state.actions.forEach { action ->
            sb.append("    ${renderAction(action)}\n")
        }

        sb.append("}")
        return sb.toString()
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun renderCondition(cond: MutableBuilderCondition): String {
        val op = cond.operator
        return when {
            OperatorOptions.isBetween(op) -> {
                val low = quoteIfNeeded(cond.value.trim())
                val high = quoteIfNeeded(cond.valueTo.trim())
                "${cond.field} between $low $high"
            }
            OperatorOptions.isList(op) -> {
                val items = cond.listItems.joinToString(", ") { quoteIfNeeded(it) }
                "${cond.field} $op [$items]"
            }
            else -> "${cond.field} $op ${quoteIfNeeded(cond.value)}"
        }
    }

    private fun renderAction(action: MutableBuilderAction): String {
        val args = action.arguments.joinToString(" ") { quoteIfNeeded(it) }
        return if (args.isBlank()) action.name else "${action.name} $args"
    }

    /**
     * Wraps [value] in double quotes if it is not already quoted and is not a
     * plain number or boolean literal.
     */
    private fun quoteIfNeeded(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) return trimmed
        if (trimmed.toDoubleOrNull() != null) return trimmed
        if (trimmed == "true" || trimmed == "false") return trimmed
        return "\"$trimmed\""
    }
}
