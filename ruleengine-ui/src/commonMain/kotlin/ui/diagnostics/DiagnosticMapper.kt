package ui.diagnostics

import ruleengine.core.errors.Severity
import ui.diagnostics.model.QuickFix
import ui.diagnostics.model.UiDiagnosticWithFix

/**
 * Utility object for mapping diagnostic information into a format suitable for UI display.
 *
 * Provides functionality to generate diagnostics with optional quick-fix suggestions
 * and human-readable hints based on the given diagnostic message, severity, and related data.
 */
object DiagnosticMapper {

    /**
     * Maps diagnostic information to a [UiDiagnosticWithFix] object by building a quick fix
     * and a hint based on the given parameters.
     *
     * @param severity How serious the diagnostic is.
     * @param message The main human-readable diagnostic message.
     * @param suggestion An optional suggestion for resolving the diagnostic, or null if not available.
     * @param line The 1-based line number where the diagnostic occurred, or null if not applicable.
     * @param column The 1-based column number where the diagnostic occurred, or null if not applicable.
     * @param file The rule file the diagnostic is about, when that is not the file on screen.
     * @return A [UiDiagnosticWithFix] object encapsulating the diagnostic message, quick fix, and optional hint.
     */
    fun map(
        severity: Severity,
        message: String,
        suggestion: String?,
        line: Int?,
        column: Int?,
        file: String? = null,
    ): UiDiagnosticWithFix {
        val quickFix = buildQuickFix(message = message, suggestion = suggestion)
        val hint = buildHint(message = message, suggestion = suggestion)
        return UiDiagnosticWithFix(
            severity = severity,
            message = message,
            hint = hint,
            line = line,
            column = column,
            quickFix = quickFix,
            file = file,
        )
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Extracts the first double-quoted token from [message] and, if [suggestion] is
     * non-blank, returns a [QuickFix.ReplaceToken] that swaps it.
     */
    private fun buildQuickFix(message: String, suggestion: String?): QuickFix {
        if (suggestion.isNullOrBlank()) return QuickFix.None
        val badToken = extractQuotedToken(message) ?: return QuickFix.None
        return QuickFix.ReplaceToken(
            label = "Replace with \"$suggestion\"",
            oldToken = badToken,
            newToken = suggestion,
        )
    }

    /**
     * Builds a human-readable hint line shown below the diagnostic message.
     * Returns null when there is nothing useful to show.
     */
    private fun buildHint(message: String, suggestion: String?): String? {
        if (suggestion.isNullOrBlank()) return null
        val badToken = extractQuotedToken(message)
        return if (badToken != null) "Did you mean \"$suggestion\"?" else suggestion
    }

    /** Returns the content of the first `"…"` pair in [text], or null. */
    private fun extractQuotedToken(text: String): String? {
        val start = text.indexOf('"')
        if (start < 0) return null
        val end = text.indexOf('"', startIndex = start + 1)
        if (end < 0) return null
        return text.substring(start + 1, end).trim().takeIf { it.isNotEmpty() }
    }
}
