package ruleengine.intellij.completion

/**
 * The part of a field path or `$variable` typed before the caret.
 *
 * Wider than the UI editor's word, which stops at a dot: IntelliJ replaces the whole prefix with the
 * chosen item, and the UI's field items are complete dotted paths, so `orders.amo` must match
 * `orders.amount` as a whole.
 */
object CompletionPrefix {
    fun before(text: CharSequence, offset: Int): String {
        var start = offset
        while (start > 0 && isPrefixChar(char = text[start - 1])) {
            start--
        }
        return text.subSequence(start, offset).toString()
    }

    private fun isPrefixChar(char: Char): Boolean {
        return char.isLetterOrDigit() || char == '_' || char == '-' || char == '.' || char == '$'
    }
}
