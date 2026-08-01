package ui.editor

import ui.editor.model.TextEdit

/**
 * The editing rules shared by the rule editor and the YAML editor.
 *
 * The two hosts look different — one has a line-number gutter and shared state, the other is a plain
 * box with local state — but they respond to Enter, Tab and the completion popup identically, bar
 * the width of one indent level and what counts as a block-opening line. Those two differences are
 * parameters here; everything else was duplicated line for line.
 */
object CodeEditing {

    /** How many completions the popup shows at once. */
    const val MAX_SUGGESTIONS = 8

    /**
     * The completions to offer for [word].
     *
     * An empty [word] means the caret is somewhere a completion makes sense but nothing has been
     * typed yet, so the candidates are offered unfiltered and in their existing order. Once a prefix
     * exists the list is narrowed to it, ordered by kind and then alphabetically, and an exact match
     * is dropped — offering a word that is already fully typed just hides the useful entries.
     */
    fun <T> filterSuggestions(
        candidates: List<T>,
        word: String,
        label: (T) -> String,
        kindOrder: (T) -> Int,
        limit: Int = MAX_SUGGESTIONS,
    ): List<T> {
        if (word.isEmpty()) {
            return candidates.take(n = limit)
        }

        return candidates
            .filter { candidate ->
                label(candidate).startsWith(prefix = word, ignoreCase = true) && label(candidate) != word
            }
            .sortedWith(comparator = compareBy({ kindOrder(it) }, { label(it) }))
            .take(n = limit)
    }

    /**
     * Enter: break the line, carrying the current indent, and add one level after a line that opens
     * a block.
     *
     * A selection is replaced, matching what typing any other character does.
     */
    fun breakLine(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        indentUnit: String,
        opensBlock: (trimmedLine: String) -> Boolean,
    ): TextEdit {
        val lineStart = text.lastIndexOf(char = '\n', startIndex = selectionStart - 1) + 1
        val currentLine = text.substring(startIndex = lineStart, endIndex = selectionStart)
        val indent = currentLine.takeWhile { character -> character == ' ' || character == '\t' }
        val extra = if (opensBlock(currentLine.trim())) indentUnit else ""

        return TextEdit(
            text = text.substring(0, selectionStart) + "\n" + indent + extra + text.substring(selectionEnd),
            cursor = selectionStart + 1 + indent.length + extra.length,
        )
    }

    /** Tab: insert one indent level, replacing any selection. */
    fun indent(text: String, selectionStart: Int, selectionEnd: Int, indentUnit: String): TextEdit {
        return TextEdit(
            text = text.substring(0, selectionStart) + indentUnit + text.substring(selectionEnd),
            cursor = selectionStart + indentUnit.length,
        )
    }

    /**
     * Shift+Tab: remove up to one indent level from the start of the caret's line.
     *
     * Returns null when the line has no leading space to give back, so the caller can leave the
     * buffer untouched rather than emitting an identical edit.
     */
    fun dedent(text: String, selectionStart: Int, indentUnit: String): TextEdit? {
        val lineStart = text.lastIndexOf(char = '\n', startIndex = selectionStart - 1) + 1
        val spaces = text.substring(startIndex = lineStart)
            .takeWhile { character -> character == ' ' }
            .length
            .coerceAtMost(maximumValue = indentUnit.length)

        if (spaces == 0) {
            return null
        }

        return TextEdit(
            text = text.substring(0, lineStart) + text.substring(startIndex = lineStart + spaces),
            cursor = (selectionStart - spaces).coerceAtLeast(minimumValue = lineStart),
        )
    }

    /**
     * Whether an open completion popup still describes where the caret is.
     *
     * The popup is anchored to the start of the word it was opened for. Typing more of that word
     * must keep it open — that is how it narrows — so it closes only once the caret moves before the
     * anchor or onto another line. Closing on *any* edit, which is the obvious implementation, makes
     * the popup vanish on the first keystroke after it opens.
     */
    fun isAnchorLive(text: String, cursor: Int, anchor: Int): Boolean {
        if (anchor < 0 || anchor > text.length || cursor < anchor) {
            return false
        }

        return !text.substring(startIndex = anchor, endIndex = cursor.coerceAtMost(text.length))
            .contains(char = '\n')
    }

    /**
     * Whether [candidate] is [current] with exactly one space added at the caret.
     *
     * A space-based shortcut can reach the field twice: once as the key event the editor consumes,
     * and once as text input, which `onPreviewKeyEvent` never sees. Detecting that lets the editor
     * drop the stray space instead of leaving it in the document.
     */
    fun isStraySpaceInsertion(current: String, caret: Int, candidate: String): Boolean {
        if (candidate.length != current.length + 1 || caret < 0 || caret > current.length) {
            return false
        }

        return candidate == current.substring(0, caret) + " " + current.substring(caret)
    }

    /** Replaces the partially-typed word at the caret with a chosen completion. */
    fun applySuggestion(text: String, wordStart: Int, cursor: Int, insertText: String): TextEdit {
        return TextEdit(
            text = text.substring(0, wordStart) + insertText + text.substring(cursor),
            cursor = wordStart + insertText.length,
        )
    }
}
