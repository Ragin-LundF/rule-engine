package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the caret→rule and rule→lines locators the Inspector reads in code mode.
 *
 * Both sit on [findRuleBlockRange], so the cases that matter are the ones its brace counting exists
 * for — a `}` inside a regex literal, a `{` inside a comment — plus the boundaries where "which rule
 * is the caret in" has no answer.
 */
class RuleAtCaretTest {

    private val twoRules = """
        # entry rules
        rule "first" {
          when
            amount >= 5
          then
            flag "small"
        }

        rule "second" {
          when
            iban regex "^DE\d{18}$"
          then
            flag "german"
        }
    """.trimIndent()

    private val ids = listOf("first", "second")

    /** Offset of the first character of the line containing [marker]. */
    private fun caretAt(text: String, marker: String): Int = text.indexOf(marker)

    @Test
    fun `caret inside a rule body names that rule`() {
        assertEquals(
            expected = "first",
            actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = caretAt(twoRules, "amount >= 5")),
        )
        assertEquals(
            expected = "second",
            actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = caretAt(twoRules, "flag \"german\"")),
        )
    }

    @Test
    fun `caret on the rule header names that rule`() {
        assertEquals(
            expected = "second",
            actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = caretAt(twoRules, "rule \"second\"")),
        )
    }

    @Test
    fun `caret on the closing brace still names the rule`() {
        // The block range is inclusive of its closing brace, and a caret parked there is still inside
        // the rule as far as the author is concerned.
        val closingBrace = twoRules.indexOf("}\n\nrule")

        assertEquals(
            expected = "first",
            actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = closingBrace),
        )
    }

    @Test
    fun `caret between two rules names nothing`() {
        val gap = twoRules.indexOf("}\n\nrule") + 2

        assertNull(actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = gap))
    }

    @Test
    fun `caret in a leading comment names nothing`() {
        assertNull(actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = 0))
    }

    @Test
    fun `a brace inside a regex literal does not end the block early`() {
        // Without brace counting the block for "second" would close at the `}` of `\d{18}`, leaving
        // everything after it — including its own actions — outside every rule.
        assertEquals(
            expected = "second",
            actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = caretAt(twoRules, "flag \"german\"")),
        )
    }

    @Test
    fun `empty text and empty id list name nothing`() {
        assertNull(actual = ruleIdAtCaret(fullText = "", ruleIds = ids, caret = 0))
        assertNull(actual = ruleIdAtCaret(fullText = twoRules, ruleIds = emptyList(), caret = 20))
    }

    @Test
    fun `a caret past the end of the text is clamped rather than throwing`() {
        assertNull(
            actual = ruleIdAtCaret(fullText = twoRules, ruleIds = ids, caret = twoRules.length + 500),
        )
    }

    @Test
    fun `line range spans the whole block, one-based`() {
        // "first" opens on line 2 — line 1 is the comment — and closes on line 7.
        assertEquals(expected = 2..7, actual = ruleLineRange(fullText = twoRules, ruleId = "first"))
        assertEquals(expected = 9..14, actual = ruleLineRange(fullText = twoRules, ruleId = "second"))
    }

    @Test
    fun `line range of an unknown rule is null`() {
        assertNull(actual = ruleLineRange(fullText = twoRules, ruleId = "third"))
    }
}
