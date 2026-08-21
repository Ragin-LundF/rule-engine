package ui.diagnostics.model

import ruleengine.core.errors.Severity
/**
 * A UI-level diagnostic enriched with an optional [quickFix].
 *
 * @param severity  How serious the diagnostic is.
 * @param message   Human-readable diagnostic message.
 * @param hint      Optional suggestion text (e.g. "Did you mean purpose?").
 * @param line      1-based line number, or null.
 * @param column    1-based column number, or null.
 * @param quickFix  Actionable fix, or [QuickFix.None] when not available.
 * @param file      The rule file this is about, when it is not the one on screen — an entry-wide
 *   validation reports every file of the entry, and a line number in another file is meaningless
 *   without it. Null for a diagnostic about the open buffer, which needs no label. A String rather
 *   than a Path so this stays usable from `commonMain`.
 */
data class UiDiagnosticWithFix(
    val severity: Severity,
    val message: String,
    val hint: String?,
    val line: Int?,
    val column: Int?,
    val quickFix: QuickFix,
    val file: String? = null,
)
