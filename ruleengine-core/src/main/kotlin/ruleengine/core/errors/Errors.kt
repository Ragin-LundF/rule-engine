package ruleengine.core.errors

import java.nio.file.Path

sealed class RuleEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class SchemaLoadException(
    val path: Path,
    val details: String
) : RuleEngineException("Failed to load schema from $path: $details")

data class ValidationDiagnostic(
    val severity: Severity,
    val message: String,
    val file: Path? = null,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null
)

enum class Severity { ERROR, WARNING, INFO }

data class CompilationException(val ruleId: String?, val details: String) : RuleEngineException("Compilation failed for rule ${ruleId ?: "<unknown>"}: $details")

