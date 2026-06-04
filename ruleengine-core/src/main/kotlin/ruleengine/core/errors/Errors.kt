package ruleengine.core.errors

import java.io.Serial
import java.nio.file.Path

sealed class RuleEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    companion object {
        @Serial
        private const val serialVersionUID: Long = -7115250465939943273L
    }
}

data class SchemaLoadException(
    val path: Path,
    val details: String
) : RuleEngineException("Failed to load schema from $path: $details") {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 8371538950540441360L
    }
}

data class ValidationDiagnostic(
    val severity: Severity,
    val message: String,
    val file: Path? = null,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null
)

enum class Severity { ERROR, WARNING, INFO }

data class CompilationException(val ruleId: String?, val details: String) :
    RuleEngineException("Compilation failed for rule ${ruleId ?: "<unknown>"}: $details") {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 2725699143590444473L
    }
}

