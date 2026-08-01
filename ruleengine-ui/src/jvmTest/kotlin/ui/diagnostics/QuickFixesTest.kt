package ui.diagnostics

import ui.diagnostics.model.QuickFix
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickFixesTest {

    @Test
    fun `ReplaceToken replaces whole-word bad token`() {
        val rule = """
            rule "bad" {
              when
                purpse contains "rent"
              then
                label "rent"
            }
        """.trimIndent()
        val fix = QuickFix.ReplaceToken(label = "Replace", oldToken = "purpse", newToken = "purpose")
        val result = QuickFixes.apply(fix = fix, ruleText = rule)
        assertEquals(true, result.contains("purpose"))
        assertEquals(false, result.contains("purpse"))
    }

    @Test
    fun `ReplaceToken does not replace partial matches`() {
        val rule = "purpse_extra contains \"rent\""
        val fix = QuickFix.ReplaceToken(label = "Replace", oldToken = "purpse", newToken = "purpose")
        val result = QuickFixes.apply(fix = fix, ruleText = rule)
        // "purpse" is part of "purpse_extra" — should NOT be replaced
        assertEquals(rule, result)
    }

    @Test
    fun `None fix returns text unchanged`() {
        val rule = "rule \"x\" { when a equals 1 then label \"y\" }"
        val result = QuickFixes.apply(fix = QuickFix.None, ruleText = rule)
        assertEquals(rule, result)
    }

    @Test
    fun `ReplaceToken replaces all occurrences`() {
        val rule = "purpse contains \"rent\" and purpse equals \"food\""
        val fix = QuickFix.ReplaceToken(label = "Replace", oldToken = "purpse", newToken = "purpose")
        val result = QuickFixes.apply(fix = fix, ruleText = rule)
        assertEquals("purpose contains \"rent\" and purpose equals \"food\"", result)
    }

    @Test
    fun `ReplaceToken with empty oldToken returns text unchanged`() {
        val rule = "some rule text"
        val fix = QuickFix.ReplaceToken(label = "Replace", oldToken = "", newToken = "x")
        val result = QuickFixes.apply(fix = fix, ruleText = rule)
        assertEquals(rule, result)
    }
}
