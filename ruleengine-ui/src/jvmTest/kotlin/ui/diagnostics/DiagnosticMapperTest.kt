package ui.diagnostics

import ruleengine.core.errors.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DiagnosticMapperTest {

    @Test
    fun `unknown field with suggestion produces ReplaceToken fix`() {
        val result = DiagnosticMapper.map(
            severity = Severity.ERROR,
            message = """Unknown field "purpse"""",
            suggestion = "purpose",
            line = 3,
            column = 5,
        )
        assertIs<QuickFix.ReplaceToken>(value = result.quickFix)
        val fix = result.quickFix
        assertEquals(expected = "purpse", actual = fix.oldToken)
        assertEquals(expected = "purpose", actual = fix.newToken)
        assertEquals(expected = """Did you mean "purpose"?""", actual = result.hint)
        assertEquals(expected = Severity.ERROR, actual = result.severity)
        assertEquals(expected = 3, actual = result.line)
        assertEquals(expected = 5, actual = result.column)
    }

    @Test
    fun `message without suggestion produces None fix`() {
        val result = DiagnosticMapper.map(
            severity = Severity.ERROR,
            message = "Syntax error near '{'",
            suggestion = null,
            line = 1,
            column = null,
        )
        assertIs<QuickFix.None>(value = result.quickFix)
        assertNull(actual = result.hint)
    }

    @Test
    fun `message with suggestion but no quoted token produces None fix`() {
        val result = DiagnosticMapper.map(
            severity = Severity.WARNING,
            message = "Operator not allowed for this field type",
            suggestion = ">=",
            line = null,
            column = null,
        )
        // No quoted token in message → cannot build ReplaceToken
        assertIs<QuickFix.None>(value = result.quickFix)
        // Hint still shows the suggestion directly
        assertEquals(expected = ">=", actual = result.hint)
    }

    @Test
    fun `severity is preserved verbatim`() {
        val result = DiagnosticMapper.map(
            severity = Severity.WARNING,
            message = """Unknown action "notfy"""",
            suggestion = "notify",
            line = null,
            column = null,
        )
        assertEquals(expected = Severity.WARNING, actual = result.severity)
        assertIs<QuickFix.ReplaceToken>(value = result.quickFix)
    }

    @Test
    fun `blank suggestion produces None fix`() {
        val result = DiagnosticMapper.map(
            severity = Severity.ERROR,
            message = """Unknown field "xyz"""",
            suggestion = "   ",
            line = null,
            column = null,
        )
        assertIs<QuickFix.None>(value = result.quickFix)
        assertNull(actual = result.hint)
    }
}
