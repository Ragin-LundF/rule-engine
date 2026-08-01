package ui.builder

import ui.builder.OperandText.LABEL_MAX_SEGMENTS
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep

/**
 * Renders [BuilderOperand] trees as text.
 *
 * Two renderings, one traversal each:
 * - [toDsl] produces engine DSL and is what [BuilderToRuleDsl] writes back to the rule file.
 * - [toLabel] produces the compact, human-readable form shown on a collapsed operand chip.
 *
 * Both are folds over the operand, so a deeply nested path costs no extra code.
 */
object OperandText {

    /** Segment separator used in chip labels — visually distinct from the DSL's `.`. */
    private const val LABEL_SEPARATOR = " ▸ "

    /** Path segments shown at each end of an elided label. */
    private const val LABEL_EDGE_SEGMENTS = 1

    /** Longest path rendered in full on a chip before the middle is elided. */
    private const val LABEL_MAX_SEGMENTS = 3

    // ── DSL ───────────────────────────────────────────────────────────────────

    fun toDsl(operand: BuilderOperand): String = when (operand) {
        is BuilderOperand.FieldRef -> pathToDsl(path = operand.path)
        is BuilderOperand.Literal -> literalToDsl(literal = operand)
        is BuilderOperand.Aggregate -> "${operand.function}(${pathToDsl(path = operand.path)})"
        is BuilderOperand.Calc -> {
            val body = operand.terms.joinToString(separator = "") { term ->
                val prefix = if (term.operator.isBlank()) "" else " ${term.operator} "
                "$prefix${toDsl(operand = term.operand)}"
            }
            if (operand.parenthesized) "($body)" else body
        }
    }

    private fun literalToDsl(literal: BuilderOperand.Literal): String {
        val trimmed = literal.text.trim()
        if (literal.numeric) return trimmed
        return quote(value = trimmed)
    }

    private fun pathToDsl(path: List<BuilderPathStep>): String =
        path.joinToString(separator = ".") { step ->
            step.name + step.filters
                .filter { it.field.isNotBlank() }
                .joinToString(separator = "") { filter -> "[${filterToDsl(filter = filter)}]" }
        }

    private fun filterToDsl(filter: BuilderFilter): String =
        "${filter.field} ${filter.operator} ${quoteUnlessNumeric(value = filter.value)}"

    // ── chip labels ───────────────────────────────────────────────────────────

    fun toLabel(operand: BuilderOperand): String = when (operand) {
        is BuilderOperand.FieldRef -> pathToLabel(path = operand.path)
        is BuilderOperand.Literal -> operand.text.ifBlank { "…" }
        is BuilderOperand.Aggregate -> "${operand.function}(${pathToLabel(path = operand.path)})"
        is BuilderOperand.Calc -> operand.terms.joinToString(separator = " ") { term ->
            val prefix = if (term.operator.isBlank()) "" else "${displayOperator(operator = term.operator)} "
            "$prefix${toLabel(operand = term.operand)}"
        }
    }

    /**
     * Names of the path segments, with the middle elided once the path grows past
     * [LABEL_MAX_SEGMENTS] so a deep path still fits on one row. Filters are indicated by a marker
     * rather than spelled out — the full text is always available in the row's DSL echo line.
     */
    private fun pathToLabel(path: List<BuilderPathStep>): String {
        val names = path.map { step ->
            if (step.filters.any { it.field.isNotBlank() }) "${step.name}*" else step.name
        }
        val shown = if (names.size <= LABEL_MAX_SEGMENTS) {
            names
        } else {
            names.take(n = LABEL_EDGE_SEGMENTS) + "…" + names.takeLast(n = LABEL_EDGE_SEGMENTS)
        }
        return shown.joinToString(separator = LABEL_SEPARATOR)
    }

    /** Arithmetic symbols read better as × and ÷ in labels than as * and /. */
    private fun displayOperator(operator: String): String = when (operator) {
        "*" -> "×"
        "/" -> "÷"
        else -> operator
    }

    // ── shared quoting ────────────────────────────────────────────────────────

    /** Wraps [value] in double quotes unless it is already quoted. */
    private fun quote(value: String): String {
        if (value.startsWith(prefix = "\"") && value.endsWith(suffix = "\"")) return value
        return "\"$value\""
    }

    /**
     * A number in canonical form, i.e. what the DSL writes back out as a number literal.
     *
     * Deliberately stricter than `toDoubleOrNull`, which accepts `10.`, `1e5` and `Infinity`. The
     * lexer reads `10.` as a number too, so emitting the text of `ip startsWith "10."` unquoted would
     * turn a text literal into a numeric one on the next parse.
     */
    private val CANONICAL_NUMBER = Regex(pattern = """-?\d+(\.\d+)?""")

    /** Leaves numeric and boolean literals bare; quotes everything else. */
    fun quoteUnlessNumeric(value: String): String {
        val trimmed = value.trim()
        if (trimmed.startsWith(prefix = "\"") && trimmed.endsWith(suffix = "\"")) return trimmed
        if (CANONICAL_NUMBER.matches(input = trimmed)) return trimmed
        if (trimmed == "true" || trimmed == "false") return trimmed
        return quote(value = trimmed)
    }
}
