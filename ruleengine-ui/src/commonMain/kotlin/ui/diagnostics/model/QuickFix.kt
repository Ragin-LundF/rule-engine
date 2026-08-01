package ui.diagnostics.model
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
