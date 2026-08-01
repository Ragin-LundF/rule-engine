package ui.workbench.model
/**
 * UI-facing severity level for diagnostics.
 * Mirrors core Severity without exposing the JVM-only core type to commonMain.
 */
enum class UiDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}
