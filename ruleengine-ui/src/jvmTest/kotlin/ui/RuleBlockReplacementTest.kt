package ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the brace-counting rule-block locator. The regex it replaced could not match a rule body
 * containing `}` — most often inside a regex pattern — and a failed match silently appended a
 * duplicate rule instead of replacing the original.
 */
class RuleBlockReplacementTest {

    private fun ruleText(id: String, condition: String): String = """
        rule "$id" {
          when
            $condition
          then
            flag "hit"
        }
    """.trimIndent()

    @Test
    fun `replaces a plain rule body`() {
        val original = ruleText(id = "a", condition = """amount >= 5""")
        val replacement = ruleText(id = "a", condition = """amount >= 10""")

        val result = replaceRuleDslBlock(fullText = original, ruleId = "a", newRuleDsl = replacement)

        assertEquals(expected = replacement, actual = result)
    }

    @Test
    fun `replaces a rule whose body contains braces inside a regex literal`() {
        val original = ruleText(id = "iban", condition = """iban regex "^DE\d{18}$"""")
        val replacement = ruleText(id = "iban", condition = """iban regex "^AT\d{16}$"""")

        val result = replaceRuleDslBlock(fullText = original, ruleId = "iban", newRuleDsl = replacement)

        assertEquals(expected = replacement, actual = result)
        assertEquals(
            expected = 1,
            actual = Regex(pattern = """rule\s+"iban"""").findAll(input = result).count(),
            message = "The rule must be replaced, not duplicated",
        )
    }

    @Test
    fun `replaces only the targeted rule when several are present`() {
        val first = ruleText(id = "first", condition = """amount >= 1""")
        val second = ruleText(id = "second", condition = """iban regex "^DE\d{18}$"""")
        val third = ruleText(id = "third", condition = """amount >= 3""")
        val original = "$first\n\n$second\n\n$third"
        val replacement = ruleText(id = "second", condition = """amount >= 99""")

        val result = replaceRuleDslBlock(fullText = original, ruleId = "second", newRuleDsl = replacement)

        assertTrue(actual = result.contains(other = replacement))
        assertTrue(actual = result.contains(other = first), message = "First rule must be untouched")
        assertTrue(actual = result.contains(other = third), message = "Third rule must be untouched")
        assertTrue(actual = !result.contains(other = """^DE\d{18}$"""), message = "Old body must be gone")
    }

    @Test
    fun `ignores braces inside comments`() {
        val original = """
            rule "commented" {
              when
                amount >= 5   # note: not a } brace
              then
                flag "hit"
            }
        """.trimIndent()
        val replacement = ruleText(id = "commented", condition = """amount >= 6""")

        val result = replaceRuleDslBlock(fullText = original, ruleId = "commented", newRuleDsl = replacement)

        assertEquals(expected = replacement, actual = result)
    }

    @Test
    fun `appends when the rule is absent`() {
        val original = ruleText(id = "a", condition = """amount >= 5""")
        val addition = ruleText(id = "b", condition = """amount >= 6""")

        val result = replaceRuleDslBlock(fullText = original, ruleId = "b", newRuleDsl = addition)

        assertEquals(expected = "$original\n$addition", actual = result)
    }

    @Test
    fun `unbalanced braces are reported as not found rather than corrupting text`() {
        val broken = """
            rule "broken" {
              when
                amount >= 5
        """.trimIndent()

        assertNull(actual = findRuleBlockRange(fullText = broken, ruleId = "broken"))
    }
}
