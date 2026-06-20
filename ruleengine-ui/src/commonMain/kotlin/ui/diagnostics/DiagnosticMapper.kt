package ui.diagnostics

/**
 * Maps raw diagnostic data (message + optional suggestion) to [UiDiagnosticWithFix].
 *
 * Quick-fix rules (MVP):
 * - If the message contains a quoted unknown token AND a suggestion is present,
 *   produce a [QuickFix.ReplaceToken] that swaps the bad token for the suggestion.
 * - Otherwise produce [QuickFix.None].
 *
 * This object is commonMain-safe: it operates only on strings.
 */
object DiagnosticMapper {

    /**
     * Converts a flat diagnostic into a [UiDiagnosticWithFix].
     *
     * @param severity   "error" or "warning" string.
     * @param message    Raw diagnostic message from the core validator.
     * @param suggestion Optional suggestion from [ValidationDiagnostic.suggestion].
     * @param line       1-based line number, or null.
     * @param column     1-based column number, or null.
     */
    fun map(
        severity: String,
        message: String,
        suggestion: String?,
        line: Int?,
        column: Int?,
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
