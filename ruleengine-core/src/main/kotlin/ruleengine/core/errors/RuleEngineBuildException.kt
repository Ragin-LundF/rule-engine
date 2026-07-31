package ruleengine.core.errors

import java.io.Serial
import java.nio.file.Path

/**
 * Thrown when a rule engine cannot be built from a manifest.
 *
 * The message names the manifest, the affected entry (when known) and the concrete problem, and
 * appends one line per validation diagnostic so the full reason is visible without a logging
 * framework. Building fails hard on purpose: a partially initialised engine must never be used.
 */
data class RuleEngineBuildException(
    val manifestPath: Path,
    val entryId: String?,
    val details: String,
    val diagnostics: List<ValidationDiagnostic> = emptyList(),
    override val cause: Throwable? = null,
) : RuleEngineException(buildMessage(manifestPath, entryId, details, diagnostics), cause) {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 4192864478115206103L

        private fun buildMessage(
            manifestPath: Path,
            entryId: String?,
            details: String,
            diagnostics: List<ValidationDiagnostic>,
        ): String = buildString {
            append("Failed to build rule engine from manifest ")
            append(manifestPath)
            entryId?.let { append(" (entry '").append(it).append("')") }
            append(": ")
            append(details)
            diagnostics.forEach { diagnostic ->
                append("\n  [").append(diagnostic.severity).append("] ").append(diagnostic.message)
                diagnostic.file?.let { append(" (file: ").append(it).append(")") }
                diagnostic.line?.let { append(" (line: ").append(it).append(")") }
                diagnostic.suggestion?.let { append(" — did you mean: ").append(it).append("?") }
            }
        }
    }
}
