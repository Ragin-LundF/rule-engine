package ui.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DiagnosticMapperTest {

    @Test
    fun `unknown field with suggestion produces ReplaceToken fix`() {
        val result = DiagnosticMapper.map(
            severity = "error",
            message = """Unknown field "purpse"""",
            suggestion = "purpose",
            line = 3,
            column = 5,
        )
        assertIs<QuickFix.ReplaceToken>(result.quickFix)
        val fix = result.quickFix as QuickFix.ReplaceToken
        assertEquals("purpse", fix.oldToken)
        assertEquals("purpose", fix.newToken)
        assertEquals("""Did you mean "purpose"?""", result.hint)
        assertEquals("error", result.severity)
        assertEquals(3, result.line)
        assertEquals(5, result.column)
    }

    @Test
    fun `message without suggestion produces None fix`() {
        val result = DiagnosticMapper.map(
            severity = "error",
            message = "Syntax error near '{'",
            suggestion = null,
            line = 1,
            column = null,
        )
        assertIs<QuickFix.None>(result.quickFix)
        assertNull(result.hint)
    }

    @Test
    fun `message with suggestion but no quoted token produces None fix`() {
        val result = DiagnosticMapper.map(
            severity = "warning",
            message = "Operator not allowed for this field type",
            suggestion = ">=",
            line = null,
            column = null,
        )
        // No quoted token in message → cannot build ReplaceToken
        assertIs<QuickFix.None>(result.quickFix)
        // Hint still shows the suggestion directly
        assertEquals(">=", result.hint)
    }

    @Test
    fun `severity is preserved verbatim`() {
        val result = DiagnosticMapper.map(
            severity = "warning",
            message = """Unknown action "notfy"""",
            suggestion = "notify",
            line = null,
            column = null,
        )
        assertEquals("warning", result.severity)
        assertIs<QuickFix.ReplaceToken>(result.quickFix)
    }

    @Test
    fun `blank suggestion produces None fix`() {
        val result = DiagnosticMapper.map(
            severity = "error",
            message = """Unknown field "xyz"""",
            suggestion = "   ",
            line = null,
            column = null,
        )
        assertIs<QuickFix.None>(result.quickFix)
        assertNull(result.hint)
    }
}
