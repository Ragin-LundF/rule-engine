package ui.workbench.model

import ruleengine.core.errors.Severity
/**
 * A single UI-facing diagnostic message produced by validation.
 * Does not reference java.nio.file.Path so it is safe in commonMain.
 */
data class UiDiagnostic(
    val severity: Severity,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null,
)
