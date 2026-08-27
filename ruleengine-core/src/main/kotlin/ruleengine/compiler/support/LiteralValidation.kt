package ruleengine.compiler.support

import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
import ruleengine.dsl.ast.ListLiteral
import ruleengine.dsl.ast.NumberLiteral
import java.math.BigDecimal

/**
 * Checks that a numeric literal in a condition is one the compiler will later accept.
 *
 * The compiler parses these same literals with the same calls and throws on failure; validating them
 * here is what turns that crash into a diagnostic the author can read.
 */
internal object LiteralValidation {

    internal fun validateDecimalLiteral(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val literal = cond.value as? NumberLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' expects numeric literal",
                line = cond.line,
                column = cond.column,
            )
            return
        }
        runCatching {
            BigDecimal(literal.value)
        }.onFailure {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Invalid decimal literal: ${literal.value}",
                line = cond.line,
                column = cond.column,
            )
        }
    }

    internal fun validateDecimalBounds(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val between = cond.value as? BetweenLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' with 'between' expects two numeric bounds",
                line = cond.line,
                column = cond.column,
            )
            return
        }
        validateDecimalBound(
            value = between.low,
            diagnostics = diagnostics,
            message = "Invalid lower bound: ${between.low}",
            line = cond.line,
            column = cond.column,
        )
        validateDecimalBound(
            value = between.high,
            diagnostics = diagnostics,
            message = "Invalid upper bound: ${between.high}",
            line = cond.line,
            column = cond.column,
        )
    }

    internal fun validateDecimalBound(
        value: String,
        diagnostics: MutableList<ValidationDiagnostic>,
        message: String,
        line: Int? = null,
        column: Int? = null,
    ) {
        runCatching {
            BigDecimal(value)
        }.onFailure {
            diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = message,
            line = line,
            column = column,
        )
        }
    }

    internal fun validateIntegerLiteral(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val literal = cond.value as? NumberLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' expects integer literal",
                line = cond.line,
                column = cond.column,
            )
            return
        }
        runCatching {
            literal.value.toLong()
        }.onFailure {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Invalid integer literal: ${literal.value}",
                line = cond.line,
                column = cond.column,
            )
        }
    }

    /**
     * The values an `in` on a numeric field tests against.
     *
     * A bare literal is accepted as a set of one, matching the compiler and matching how `in` already
     * reads on a text field — the brackets are what a reader expects, not what the grammar demands.
     * Each item is checked with the same bound helper `between` uses, so one bad entry in a long list
     * names itself instead of failing the whole condition anonymously.
     */
    internal fun validateNumericMembership(
        cond: ConditionAst,
        diagnostics: MutableList<ValidationDiagnostic>,
        whole: Boolean,
    ) {
        val kind = if (whole) "integer" else "numeric"
        val items = when (val literal = cond.value) {
            is ListLiteral -> literal.items
            is NumberLiteral -> listOf(literal)
            else -> {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Field '${cond.field}' with 'in' expects a list of $kind literals",
                    line = cond.line,
                    column = cond.column,
                )
                return
            }
        }
        for (item in items) {
            val number = (item as? NumberLiteral)?.value
            if (number == null) {
                diagnostics += ValidationDiagnostic(
                    severity = Severity.ERROR,
                    message = "Field '${cond.field}' with 'in' expects $kind literals",
                    line = cond.line,
                    column = cond.column,
                )
                continue
            }
            val message = "Invalid $kind literal: $number"
            if (whole) {
                validateIntegerBound(
                    value = number,
                    diagnostics = diagnostics,
                    message = message,
                    line = cond.line,
                    column = cond.column,
                )
            } else {
                validateDecimalBound(
                    value = number,
                    diagnostics = diagnostics,
                    message = message,
                    line = cond.line,
                    column = cond.column,
                )
            }
        }
    }

    internal fun validateIntegerBounds(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val between = cond.value as? BetweenLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' with 'between' expects two integer bounds",
                line = cond.line,
                column = cond.column,
            )
            return
        }
        validateIntegerBound(
            value = between.low,
            diagnostics = diagnostics,
            message = "Invalid lower bound: ${between.low}",
            line = cond.line,
            column = cond.column,
        )
        validateIntegerBound(
            value = between.high,
            diagnostics = diagnostics,
            message = "Invalid upper bound: ${between.high}",
            line = cond.line,
            column = cond.column,
        )
    }

    internal fun validateIntegerBound(
        value: String,
        diagnostics: MutableList<ValidationDiagnostic>,
        message: String,
        line: Int? = null,
        column: Int? = null,
    ) {
        runCatching {
            value.toLong()
        }.onFailure {
            diagnostics += ValidationDiagnostic(
            severity = Severity.ERROR,
            message = message,
            line = line,
            column = column,
        )
        }
    }
}
