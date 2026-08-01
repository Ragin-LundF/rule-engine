package ui.workbench

import ruleengine.compiler.Validator
import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.UiDiagnosticSeverity
import ui.workbench.model.ValidationState
import ui.workbench.model.WorkbenchValidationResult

/**
 * JVM implementation of [WorkbenchValidator].
 * Calls core parser/validator APIs and maps results to UI-safe types.
 * Never propagates exceptions — all errors are returned as [UiDiagnostic] items.
 */
class JvmWorkbenchValidator : WorkbenchValidator {

    override fun validate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
    ): WorkbenchValidationResult {
        val diagnostics = mutableListOf<UiDiagnostic>()

        // 1. Parse field schema
        val schema = if (schemaText.isBlank()) {
            diagnostics += UiDiagnostic(
                severity = UiDiagnosticSeverity.WARNING,
                message = "No field schema loaded — validation may be incomplete",
            )
            null
        } else {
            runCatching { FieldSchemaLoader.loadFromString(content = schemaText, nameHint = "schema") }
                .onFailure { e ->
                    diagnostics += UiDiagnostic(
                        severity = UiDiagnosticSeverity.ERROR,
                        message = "Field schema parse error: ${e.message}",
                    )
                }
                .getOrNull()
        }

        // 2. Parse action schema (optional)
        val actionSchema = if (actionsText.isBlank()) {
            null
        } else {
            runCatching { ActionSchemaLoader.loadFromString(content = actionsText) }
                .onFailure { e ->
                    diagnostics += UiDiagnostic(
                        severity = UiDiagnosticSeverity.ERROR,
                        message = "Action schema parse error: ${e.message}",
                    )
                }
                .getOrNull()
        }

        // 3. Parse rule DSL
        val asts = if (ruleText.isBlank()) {
            diagnostics += UiDiagnostic(
                severity = UiDiagnosticSeverity.INFO,
                message = "No rule text to validate",
            )
            emptyList()
        } else {
            runCatching { Parser(input = ruleText).parseRules() }
                .onFailure { e ->
                    diagnostics += UiDiagnostic(
                        severity = UiDiagnosticSeverity.ERROR,
                        message = "Rule parse error: ${e.message}",
                    )
                }
                .getOrElse { emptyList() }
        }

        // 4. Semantic validation (only when schema is available)
        if (schema != null && asts.isNotEmpty()) {
            runCatching { Validator.validate(asts = asts, schema = schema, actions = actionSchema) }
                .onSuccess { result ->
                    result.diagnostics.mapTo(diagnostics) { it.toUiDiagnostic() }
                }
                .onFailure { e ->
                    diagnostics += UiDiagnostic(
                        severity = UiDiagnosticSeverity.ERROR,
                        message = "Validation error: ${e.message}",
                    )
                }
        }

        val hasError = diagnostics.any { it.severity == UiDiagnosticSeverity.ERROR }
        val validationState = when {
            ruleText.isBlank() && schemaText.isBlank() -> ValidationState.IDLE
            hasError -> ValidationState.INVALID
            else -> ValidationState.VALID
        }

        return WorkbenchValidationResult(
            diagnostics = diagnostics,
            validationState = validationState,
        )
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private fun ValidationDiagnostic.toUiDiagnostic(): UiDiagnostic = UiDiagnostic(
        severity = severity.toUiSeverity(),
        message = message,
        line = line,
        column = column,
        suggestion = suggestion,
    )

    private fun Severity.toUiSeverity(): UiDiagnosticSeverity = when (this) {
        Severity.ERROR -> UiDiagnosticSeverity.ERROR
        Severity.WARNING -> UiDiagnosticSeverity.WARNING
        Severity.INFO -> UiDiagnosticSeverity.INFO
    }
}
