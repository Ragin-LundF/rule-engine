package ui.editor.rules

import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Attribution of a diagnostic to the file the editor is showing.
 *
 * The entry-wide pass reports every file, and each diagnostic's line is relative to its own — so
 * underlining the lot would mark lines of the open file for problems in another one. This is the guard
 * against that.
 */
class EntryValidationTest {

    @Test
    fun `a diagnostic with no file belongs to whatever is open`() {
        val diagnostic = diagnostic(file = null)

        assertTrue(actual = diagnostic.isAbout(openFile = "rules/a.rule"))
        assertTrue(actual = diagnostic.isAbout(openFile = null))
    }

    @Test
    fun `a diagnostic about the open file belongs to it`() {
        assertTrue(
            actual = diagnostic(file = "rules/a.rule").isAbout(openFile = "rules/a.rule"),
        )
    }

    @Test
    fun `a diagnostic about another file does not`() {
        assertFalse(
            actual = diagnostic(file = "rules/b.rule").isAbout(openFile = "rules/a.rule"),
            message = "another file's line numbers must not underline the open buffer",
        )
    }

    @Test
    fun `a diagnostic about a file is not shown while the whole entry is in one buffer`() {
        assertFalse(actual = diagnostic(file = "rules/b.rule").isAbout(openFile = null))
    }

    private fun diagnostic(file: String?): ValidationDiagnostic {
        return ValidationDiagnostic(
            severity = Severity.ERROR,
            message = "something",
            file = file?.let { path -> Path.of(path) },
            line = 12,
        )
    }
}
