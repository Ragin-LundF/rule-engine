package ui.workbench.model
/**
 * A single UI-facing diagnostic message produced by validation.
 * Does not reference java.nio.file.Path so it is safe in commonMain.
 */
data class UiDiagnostic(
    val severity: UiDiagnosticSeverity,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null,
)
