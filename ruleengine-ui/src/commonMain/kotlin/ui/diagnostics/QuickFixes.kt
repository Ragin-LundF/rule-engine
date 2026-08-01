package ui.diagnostics

import ui.diagnostics.model.QuickFix

/**
 * Applies a [QuickFix] to the current rule text and returns the updated text.
 *
 * All operations are pure string transformations — no side effects.
 * The caller is responsible for writing the result back to the editor state,
 * which will trigger re-validation automatically.
 */
object QuickFixes {

    /**
     * Applies [fix] to [ruleText] and returns the modified text.
     * Returns [ruleText] unchanged when [fix] is [QuickFix.None] or cannot be applied.
     */
    fun apply(fix: QuickFix, ruleText: String): String = when (fix) {
        is QuickFix.ReplaceToken -> replaceToken(
            text = ruleText,
            oldToken = fix.oldToken,
            newToken = fix.newToken,
        )
        QuickFix.None -> ruleText
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Replaces whole-word occurrences of [oldToken] with [newToken] in [text].
     *
     * Uses a word-boundary approach: the token must be surrounded by non-word
     * characters (whitespace, quotes, braces, newlines) or be at the start/end
     * of the string. This prevents partial replacements (e.g. "purpse" inside
     * "purpse_extra" would not be replaced).
     */
    private fun replaceToken(text: String, oldToken: String, newToken: String): String {
        if (oldToken.isEmpty()) return text
        val escaped = Regex.escape(oldToken)
        val pattern = Regex("(?<![\\w])$escaped(?![\\w])")
        return pattern.replace(text, newToken)
    }
}
