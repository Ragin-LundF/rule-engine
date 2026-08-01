package ui.diagnostics
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
