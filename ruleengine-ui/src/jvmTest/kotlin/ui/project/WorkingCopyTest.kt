package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Disk is read when a project is opened, and not again.
 *
 * Navigating between rule files used to re-read each one, so an edit that had not been saved was
 * handed back as the text on disk and silently lost — the Builder's edits most visibly, since it
 * writes to the buffer on every click. The loaded files are a working copy for the session; the user
 * goes back to disk by opening the project again.
 */
class WorkingCopyTest {

    private fun ruleText(ruleId: String): String = """
        rule "$ruleId" {
          when purpose equals "rent"
          then label "rent"
        }
    """.trimIndent()

    private fun createProject(): Path {
        val root = Files.createTempDirectory("working-copy")
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(root.resolve("rules/a.rule"), ruleText(ruleId = "alpha"))
        Files.writeString(root.resolve("rules/b.rule"), ruleText(ruleId = "beta"))
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                name: working-copy
                entries:
                  - id: only
                    rules:
                      - rules/a.rule
                      - rules/b.rule
            """.trimIndent(),
        )
        return root
    }

    private fun openProject(root: Path): Pair<ProjectWorkspace, RuleEditorState> {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { root.resolve("manifest.yaml") },
        )
        workspace.openProject()
        return workspace to state
    }

    @Test
    fun `an unsaved edit survives switching to another file and back`() {
        val (_, state) = openProject(root = createProject())
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")

        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        state.loadSingleManifestRuleFile(relativePath = "rules/b.rule")
        assertFalse(
            actual = state.ruleValue.value.text.contains(other = "# edited"),
            message = "the other file must not show the edit",
        )

        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")
        assertTrue(
            actual = state.ruleValue.value.text.contains(other = "# edited"),
            message = "edit was lost: ${state.ruleValue.value.text}",
        )
    }

    @Test
    fun `an unsaved edit survives switching to All files and back`() {
        val (_, state) = openProject(root = createProject())
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")
        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        state.loadAllRuleFilesForCurrentEntry()

        assertTrue(
            actual = state.ruleValue.value.text.contains(other = "# edited"),
            message = "All files must show the edit: ${state.ruleValue.value.text}",
        )
    }

    /** Nothing is written behind the user's back — the edit lives in the session only. */
    @Test
    fun `an unsaved edit is not written to disk`() {
        val root = createProject()
        val (_, state) = openProject(root = root)
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")

        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")
        state.loadSingleManifestRuleFile(relativePath = "rules/b.rule")

        assertFalse(actual = Files.readString(root.resolve("rules/a.rule")).contains(other = "# edited"))
    }

    /** Opening the project again is how the user asks for what is on disk. */
    @Test
    fun `reopening the project goes back to disk`() {
        val root = createProject()
        val (workspace, state) = openProject(root = root)
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")
        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        state.reset()
        workspace.openProject()
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")

        assertFalse(
            actual = state.ruleValue.value.text.contains(other = "# edited"),
            message = "reopening must show what is on disk: ${state.ruleValue.value.text}",
        )
    }

    /** A file the working copy has never seen is still read — switching entries depends on it. */
    @Test
    fun `a file not in the working copy is read from disk`() {
        val (_, state) = openProject(root = createProject())
        state.loadSingleManifestRuleFile(relativePath = "rules/a.rule")
        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        state.loadSingleManifestRuleFile(relativePath = "rules/b.rule")

        assertTrue(actual = state.ruleValue.value.text.contains(other = "beta"))
    }

    @Test
    fun `the working copy is filled when the project opens`() {
        val (_, state) = openProject(root = createProject())

        assertEquals(
            expected = listOf("rules/a.rule", "rules/b.rule"),
            actual = state.inMemoryRuleFiles.value.keys.sorted(),
        )
    }
}
