package ruleengine.core.errors

import java.nio.file.Path

data class ValidationDiagnostic(
    val severity: Severity,
    val message: String,
    val file: Path? = null,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null
)
