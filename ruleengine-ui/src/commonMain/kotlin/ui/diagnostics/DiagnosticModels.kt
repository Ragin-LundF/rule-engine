package ui.diagnostics

/**
 * A quick-fix action that can be applied to the rule text to repair a diagnostic.
 * All variants are safe text substitutions — they never run without explicit user action.
 */
sealed class QuickFix {
    /** Replace every occurrence of [oldToken] with [newToken] in the rule text. */
    data class ReplaceToken(
        val label: String,
        val oldToken: String,
        val newToken: String,
    ) : QuickFix()

    /** No automatic fix available — only informational hint. */
    data object None : QuickFix()
}

/**
 * A UI-level diagnostic enriched with an optional [quickFix].
 *
 * @param severity  "error" or "warning".
 * @param message   Human-readable diagnostic message.
 * @param hint      Optional suggestion text (e.g. "Did you mean purpose?").
 * @param line      1-based line number, or null.
 * @param column    1-based column number, or null.
 * @param quickFix  Actionable fix, or [QuickFix.None] when not available.
 */
data class UiDiagnosticWithFix(
    val severity: String,
    val message: String,
    val hint: String?,
    val line: Int?,
    val column: Int?,
    val quickFix: QuickFix,
)
