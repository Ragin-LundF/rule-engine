package ui.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Enter/Tab rules used to live inline in two key handlers and had no coverage at all — a change
 * to either could only be caught by typing in the app. These pin the behaviour both editors had
 * before the rules were shared, including the cases that differ per editor: the indent width, and
 * what counts as a line that opens a block.
 */
class CodeEditingTest {

    private val dsl = "    "
    private val yaml = "  "

    // ── Enter ─────────────────────────────────────────────────────────────────

    @Test
    fun `enter carries the current indent onto the new line`() {
        val text = "rule {\n    amount > 1"
        val edit = CodeEditing.breakLine(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            indentUnit = dsl,
            opensBlock = { false },
        )

        assertEquals(expected = "rule {\n    amount > 1\n    ", actual = edit.text)
        assertEquals(expected = edit.text.length, actual = edit.cursor)
    }

    @Test
    fun `enter adds one extra level after a block-opening line`() {
        val text = "rule {"
        val edit = CodeEditing.breakLine(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            indentUnit = dsl,
            opensBlock = { line -> line.endsWith(char = '{') },
        )

        assertEquals(expected = "rule {\n    ", actual = edit.text)
    }

    @Test
    fun `enter uses the yaml indent width for yaml`() {
        val text = "fields:"
        val edit = CodeEditing.breakLine(
            text = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            indentUnit = yaml,
            opensBlock = { line -> line.endsWith(char = ':') },
        )

        assertEquals(expected = "fields:\n  ", actual = edit.text)
    }

    @Test
    fun `enter replaces a selection, like typing any other character`() {
        val text = "abcdef"
        val edit = CodeEditing.breakLine(
            text = text,
            selectionStart = 2,
            selectionEnd = 4,
            indentUnit = dsl,
            opensBlock = { false },
        )

        assertEquals(expected = "ab\nef", actual = edit.text)
        assertEquals(expected = 3, actual = edit.cursor)
    }

    // ── Tab ───────────────────────────────────────────────────────────────────

    @Test
    fun `tab inserts one indent level at the caret`() {
        val edit = CodeEditing.indent(text = "ab", selectionStart = 1, selectionEnd = 1, indentUnit = dsl)

        assertEquals(expected = "a    b", actual = edit.text)
        assertEquals(expected = 5, actual = edit.cursor)
    }

    @Test
    fun `tab replaces a selection`() {
        val edit = CodeEditing.indent(text = "abcd", selectionStart = 1, selectionEnd = 3, indentUnit = dsl)

        assertEquals(expected = "a    d", actual = edit.text)
    }

    @Test
    fun `shift-tab removes one indent level from the caret's line`() {
        val text = "rule {\n        deep"
        val edit = CodeEditing.dedent(text = text, selectionStart = text.length, indentUnit = dsl)

        assertEquals(expected = "rule {\n    deep", actual = edit?.text)
    }

    @Test
    fun `shift-tab removes only what is there when the line is under-indented`() {
        val text = "\n  two"
        val edit = CodeEditing.dedent(text = text, selectionStart = text.length, indentUnit = dsl)

        assertEquals(expected = "\ntwo", actual = edit?.text)
    }

    @Test
    fun `shift-tab on an unindented line changes nothing`() {
        assertNull(actual = CodeEditing.dedent(text = "flush", selectionStart = 5, indentUnit = dsl))
    }

    // ── completions ───────────────────────────────────────────────────────────

    private data class Candidate(val label: String, val order: Int)

    private fun filter(word: String, vararg candidates: Candidate): List<String> {
        return CodeEditing.filterSuggestions(
            candidates = candidates.toList(),
            word = word,
            label = { it.label },
            kindOrder = { it.order },
        ).map { it.label }
    }

    @Test
    fun `an empty prefix offers the candidates unfiltered and in their own order`() {
        assertEquals(
            expected = listOf("zebra", "alpha"),
            actual = filter("", Candidate("zebra", 1), Candidate("alpha", 0)),
        )
    }

    @Test
    fun `a prefix filters case-insensitively and orders by kind then label`() {
        assertEquals(
            expected = listOf("alpha", "Album"),
            actual = filter("al", Candidate("Album", 1), Candidate("alpha", 0), Candidate("beta", 0)),
        )
    }

    @Test
    fun `an exactly-typed word is not offered back`() {
        assertEquals(
            expected = listOf("alphabet"),
            actual = filter("alpha", Candidate("alpha", 0), Candidate("alphabet", 0)),
        )
    }

    @Test
    fun `no more than the popup's capacity is returned`() {
        val many = (1..20).map { index -> Candidate(label = "item$index", order = 0) }
        val shown = CodeEditing.filterSuggestions(
            candidates = many,
            word = "",
            label = { it.label },
            kindOrder = { it.order },
        )

        assertEquals(expected = CodeEditing.MAX_SUGGESTIONS, actual = shown.size)
    }

    // ── accepting a completion ────────────────────────────────────────────────

    @Test
    fun `accepting a completion replaces the partially typed word`() {
        val edit = CodeEditing.applySuggestion(
            text = "when amo",
            wordStart = 5,
            cursor = 8,
            insertText = "amount",
        )

        assertEquals(expected = "when amount", actual = edit.text)
        assertEquals(expected = 11, actual = edit.cursor)
    }

    @Test
    fun `accepting a completion keeps whatever follows the caret`() {
        val edit = CodeEditing.applySuggestion(
            text = "when amo > 5",
            wordStart = 5,
            cursor = 8,
            insertText = "amount",
        )

        assertEquals(expected = "when amount > 5", actual = edit.text)
    }

    // ── popup lifetime ────────────────────────────────────────────────────────

    @Test
    fun `the popup stays open while the word it was opened for grows`() {
        // opened at offset 5 ("when |"), then "amo" typed
        assertTrue(actual = CodeEditing.isAnchorLive(text = "when amo", cursor = 8, anchor = 5))
    }

    @Test
    fun `the popup closes once the caret moves before its anchor`() {
        assertFalse(actual = CodeEditing.isAnchorLive(text = "when amo", cursor = 4, anchor = 5))
    }

    @Test
    fun `the popup closes once the caret reaches another line`() {
        assertFalse(actual = CodeEditing.isAnchorLive(text = "when amo\nnext", cursor = 12, anchor = 5))
    }

    @Test
    fun `an unopened popup has no live anchor`() {
        assertFalse(actual = CodeEditing.isAnchorLive(text = "abc", cursor = 1, anchor = -1))
    }

    // ── stray space from a space-based shortcut ───────────────────────────────

    @Test
    fun `a single space added at the caret is recognised as the shortcut's stray space`() {
        assertTrue(
            actual = CodeEditing.isStraySpaceInsertion(current = "when amo", caret = 8, candidate = "when amo "),
        )
    }

    @Test
    fun `a space typed somewhere else is not treated as stray`() {
        assertFalse(
            actual = CodeEditing.isStraySpaceInsertion(current = "when amo", caret = 4, candidate = "when amo "),
        )
    }

    @Test
    fun `a real character is never swallowed`() {
        assertFalse(
            actual = CodeEditing.isStraySpaceInsertion(current = "when amo", caret = 8, candidate = "when amou"),
        )
    }
}
