package ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The three helpers that were each written twice or more before being shared. None had a test; the
 * YAML quoting in particular decides whether a saved manifest can be loaded back at all.
 */
class UtilsTest {

    // ── word under the caret ──────────────────────────────────────────────────

    @Test
    fun `the word is what has been typed of it, not the whole token`() {
        assertEquals(expected = Pair(5, "amo"), actual = Words.currentWord(text = "when amo", cursorPos = 8))
    }

    @Test
    fun `a dot ends the word, so a path completes on its last segment`() {
        assertEquals(
            expected = Pair(12, "amo"),
            actual = Words.currentWord(text = "when orders.amo", cursorPos = 15),
        )
    }

    @Test
    fun `underscores and hyphens are part of a word`() {
        assertEquals(expected = 0, actual = Words.wordStart(text = "some_rule-id", cursorPos = 12))
    }

    @Test
    fun `a caret against a separator yields an empty word`() {
        assertEquals(expected = Pair(5, ""), actual = Words.currentWord(text = "when ", cursorPos = 5))
    }

    @Test
    fun `an out-of-range caret is clamped rather than throwing`() {
        assertEquals(expected = Pair(0, "abc"), actual = Words.currentWord(text = "abc", cursorPos = 99))
    }

    // ── slugs ─────────────────────────────────────────────────────────────────

    @Test
    fun `runs of punctuation collapse to one hyphen`() {
        assertEquals(
            expected = "my-project-v2",
            actual = Slugs.slugify(value = "My Project // v2", fallback = "x"),
        )
    }

    @Test
    fun `leading and trailing separators are trimmed`() {
        assertEquals(expected = "entry", actual = Slugs.slugify(value = "  --entry--  ", fallback = "x"))
    }

    @Test
    fun `a name with nothing latin falls back rather than producing an empty file name`() {
        assertEquals(expected = "rule-overview", actual = Slugs.slugify(value = "日本語", fallback = "rule-overview"))
    }

    // ── YAML scalars ──────────────────────────────────────────────────────────

    @Test
    fun `an ordinary value is written unquoted`() {
        assertEquals(expected = "rules/main.rule", actual = YamlScalars.quoteIfNeeded(value = "rules/main.rule"))
    }

    @Test
    fun `a value opening with an indicator character is quoted`() {
        assertEquals(expected = "\"*star\"", actual = YamlScalars.quoteIfNeeded(value = "*star"))
    }

    @Test
    fun `a colon-space sequence is quoted, because a parser would read it as structure`() {
        assertEquals(expected = "\"a: b\"", actual = YamlScalars.quoteIfNeeded(value = "a: b"))
    }

    @Test
    fun `a comment marker is quoted`() {
        assertEquals(expected = "\"a #b\"", actual = YamlScalars.quoteIfNeeded(value = "a #b"))
    }

    @Test
    fun `surrounding whitespace is preserved by quoting`() {
        assertEquals(expected = "\" pad \"", actual = YamlScalars.quoteIfNeeded(value = " pad "))
    }

    @Test
    fun `an empty value is quoted rather than written as nothing`() {
        assertEquals(expected = "\"\"", actual = YamlScalars.quoteIfNeeded(value = ""))
    }

    @Test
    fun `quotes and backslashes are escaped inside a quoted scalar`() {
        assertEquals(
            expected = """"a\\b\"c: d"""",
            actual = YamlScalars.quoteIfNeeded(value = """a\b"c: d"""),
        )
    }
}
