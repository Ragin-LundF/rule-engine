package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dock marks the line a selected condition row generated. This is the function that finds it, and
 * the reason it takes a block rather than searching the file: the panel it replaces matched trimmed
 * lines document-wide, so an identical row in an unrelated rule lit up next to the real one.
 */
class RowLineRangesTest {

    /**
     * The shape `BuilderToRuleDsl` actually writes — one clause per line, the row text *last* on its
     * line after the indent and any join keyword (`"$indentStr$joinStr$notStr${renderConditionLine(..)}"`).
     * That is why the match is `endsWith` on the trimmed line, and a fixture that put the row inside
     * braces would be testing a format the generator never emits.
     */
    private val file = """
        rule "first" {
          when
            amount >= 300
          then
            label rent
        }

        rule "second" {
          when
            amount >= 300
          then
            label other
        }
    """.trimIndent()

    private fun blockOf(ruleId: String): IntRange =
        requireNotNull(findRuleBlockRange(fullText = file, ruleId = ruleId)) { "no block for $ruleId" }

    private fun linesAt(ranges: List<IntRange>): List<String> =
        ranges.map { range -> file.substring(range.first, range.last + 1) }

    /**
     * The row appears in both rules, and only the one inside the given block is returned. This is the
     * whole point of the signature.
     */
    @Test
    fun `an identical row in another rule is not matched`() {
        val inFirst = rowLineRanges(fullText = file, block = blockOf(ruleId = "first"), rowText = "amount >= 300")
        val inSecond = rowLineRanges(fullText = file, block = blockOf(ruleId = "second"), rowText = "amount >= 300")

        assertEquals(expected = 1, actual = inFirst.size)
        assertEquals(expected = 1, actual = inSecond.size)
        assertTrue(actual = inFirst.single().first < inSecond.single().first)
        assertEquals(expected = listOf("    amount >= 300"), actual = linesAt(ranges = inFirst))
    }

    /** The caller supplies the row's own text; the generator's indentation and joins are not its job. */
    @Test
    fun `the match ignores indentation and any leading join keyword`() {
        val joined = """
            rule "r" {
              when
                purpose contains "rent"
                and amount >= 300
            }
        """.trimIndent()
        val block = requireNotNull(findRuleBlockRange(fullText = joined, ruleId = "r"))

        val ranges = rowLineRanges(fullText = joined, block = block, rowText = "amount >= 300")

        assertEquals(expected = 1, actual = ranges.size)
        assertEquals(
            expected = "    and amount >= 300",
            actual = joined.substring(ranges.single().first, ranges.single().last + 1),
        )
    }

    /**
     * Two identical rows inside one rule are legal, if pointless, and genuinely ambiguous. Both are
     * returned; picking one would be a claim about the selection that is wrong half the time.
     */
    @Test
    fun `duplicate rows inside one rule both match`() {
        val duplicated = """
            rule "r" {
              when
                amount >= 300
                and amount >= 300
            }
        """.trimIndent()
        val block = requireNotNull(findRuleBlockRange(fullText = duplicated, ruleId = "r"))

        val ranges = rowLineRanges(fullText = duplicated, block = block, rowText = "amount >= 300")

        assertEquals(expected = 2, actual = ranges.size)
    }

    @Test
    fun `a row the block does not contain resolves to nothing`() {
        val ranges = rowLineRanges(
            fullText = file,
            block = blockOf(ruleId = "first"),
            rowText = "label other",
        )
        assertTrue(actual = ranges.isEmpty())
    }

    @Test
    fun `blank inputs and an out of bounds block are safe`() {
        assertTrue(actual = rowLineRanges(fullText = file, block = blockOf("first"), rowText = "   ").isEmpty())
        assertTrue(actual = rowLineRanges(fullText = "", block = 0..10, rowText = "x").isEmpty())
        assertTrue(actual = rowLineRanges(fullText = file, block = -5..9_999, rowText = "label rent").isNotEmpty())
    }

    /** The returned range is exactly one line — no trailing newline, so the mark does not bleed. */
    @Test
    fun `the range covers the line without its newline`() {
        val range = rowLineRanges(
            fullText = file,
            block = blockOf(ruleId = "first"),
            rowText = "label rent",
        ).single()

        val slice = file.substring(range.first, range.last + 1)
        assertTrue(actual = !slice.contains(char = '\n'), message = slice)
        assertEquals(expected = "    label rent", actual = slice)
    }
}
