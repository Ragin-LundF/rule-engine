package ruleengine.intellij.diagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleSegmentsTest {
    private val ruleA = "rule \"a\" { when x > 1 then stop }"
    private val text = "# head\n$ruleA\n\nrule \"b\" {\n  when y[z > 1] then stop\n}\nrule \"c\" { when"

    @Test
    fun `picks the rule around the caret`() {
        val ruleText = RuleSegments.ruleTextAt(text = text, offset = text.indexOf("y["))

        assertEquals(expected = "rule \"b\" {\n  when y[z > 1] then stop\n}", actual = ruleText)
    }

    @Test
    fun `between rules picks the one before and before all rules the first`() {
        val betweenRules = text.indexOf("\n\n") + 1

        assertEquals(expected = ruleA, actual = RuleSegments.ruleTextAt(text = text, offset = betweenRules))
        assertEquals(expected = ruleA, actual = RuleSegments.ruleTextAt(text = text, offset = 0))
    }

    @Test
    fun `an unclosed rule runs to the end of the file`() {
        val ruleText = RuleSegments.ruleTextAt(text = text, offset = text.length)

        assertEquals(expected = "rule \"c\" { when", actual = ruleText)
    }

    @Test
    fun `a file without rules has no rule text`() {
        assertNull(actual = RuleSegments.ruleTextAt(text = "# only a comment", offset = 3))
    }
}
