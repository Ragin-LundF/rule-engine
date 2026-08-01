package ui.util

/**
 * Where the word under the caret starts and what has been typed of it.
 *
 * "Word" here is what a completion prefix may contain — letters, digits, `_` and `-` — which is
 * deliberately narrower than a DSL identifier: a dot ends the word so that `orders.amo` completes
 * against `amo` rather than the whole path.
 */
object Words {

    private fun isWordCharacter(character: Char): Boolean {
        return character.isLetterOrDigit() || character == '_' || character == '-'
    }

    /** The index at which the word containing [cursorPos] begins. */
    fun wordStart(text: String, cursorPos: Int): Int {
        val cursor = cursorPos.coerceIn(0, text.length)
        var start = cursor
        while (start > 0 && isWordCharacter(character = text[start - 1])) {
            start--
        }

        return start
    }

    /** The word start and the text between it and the caret. */
    fun currentWord(text: String, cursorPos: Int): Pair<Int, String> {
        val cursor = cursorPos.coerceIn(0, text.length)
        val start = wordStart(text = text, cursorPos = cursor)

        return Pair(start, text.substring(startIndex = start, endIndex = cursor))
    }
}
