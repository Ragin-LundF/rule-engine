package ruleengine.compiler

import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.ast.BetweenLiteral
import ruleengine.dsl.ast.ConditionAst
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
                message = "Field '${cond.field}' expects numeric literal"
            )
            return
        }
        runCatching {
            BigDecimal(literal.value)
        }.onFailure {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Invalid decimal literal: ${literal.value}"
            )
        }
    }

    internal fun validateDecimalBounds(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val between = cond.value as? BetweenLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' with 'between' expects two numeric bounds"
            )
            return
        }
        validateDecimalBound(
            value = between.low,
            diagnostics = diagnostics,
            message = "Invalid lower bound: ${between.low}"
        )
        validateDecimalBound(
            value = between.high,
            diagnostics = diagnostics,
            message = "Invalid upper bound: ${between.high}"
        )
    }

    internal fun validateDecimalBound(
        value: String,
        diagnostics: MutableList<ValidationDiagnostic>,
        message: String
    ) {
        runCatching {
            BigDecimal(value)
        }.onFailure {
            diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = message)
        }
    }

    internal fun validateIntegerLiteral(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val literal = cond.value as? NumberLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' expects integer literal"
            )
            return
        }
        runCatching {
            literal.value.toLong()
        }.onFailure {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Invalid integer literal: ${literal.value}"
            )
        }
    }

    internal fun validateIntegerBounds(cond: ConditionAst, diagnostics: MutableList<ValidationDiagnostic>) {
        val between = cond.value as? BetweenLiteral ?: run {
            diagnostics += ValidationDiagnostic(
                severity = Severity.ERROR,
                message = "Field '${cond.field}' with 'between' expects two integer bounds"
            )
            return
        }
        validateIntegerBound(
            value = between.low,
            diagnostics = diagnostics,
            message = "Invalid lower bound: ${between.low}"
        )
        validateIntegerBound(
            value = between.high,
            diagnostics = diagnostics,
            message = "Invalid upper bound: ${between.high}"
        )
    }

    internal fun validateIntegerBound(
        value: String,
        diagnostics: MutableList<ValidationDiagnostic>,
        message: String
    ) {
        runCatching {
            value.toLong()
        }.onFailure {
            diagnostics += ValidationDiagnostic(severity = Severity.ERROR, message = message)
        }
    }
}
