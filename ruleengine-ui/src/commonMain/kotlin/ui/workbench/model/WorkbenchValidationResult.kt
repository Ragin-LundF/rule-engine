package ui.workbench.model
/**
 * The outcome of a single validation run.
 */
data class WorkbenchValidationResult(
    val diagnostics: List<UiDiagnostic>,
    val validationState: ValidationState,
)
