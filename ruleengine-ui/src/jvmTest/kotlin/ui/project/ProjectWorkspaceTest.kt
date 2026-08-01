package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectWorkspaceTest {

    @Test
    fun `opening with unsaved work asks first`() {
        val state = scratchState()
        val workspace = ProjectWorkspace(state = state, chooseManifestToOpen = { fail() })

        workspace.openProject()

        val dialog = assertIs<ProjectDialog.UnsavedChanges>(value = workspace.dialog.value)
        assertEquals(expected = PendingProjectAction.OpenProject, actual = dialog.pending)
    }

    /**
     * Discard used to bounce straight back into the dirty check and re-raise the same dialog, so the
     * button looked dead. The answer has been given by then; the open must actually happen.
     */
    @Test
    fun `discard actually opens the chosen project`() {
        val state = scratchState()
        val project = createProject(ruleId = "opened-rule")
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { project.resolve("manifest.yaml") },
        )

        workspace.openProject()
        workspace.onUnsavedChangesDiscard(pending = PendingProjectAction.OpenProject)

        assertNull(actual = workspace.dialog.value)
        assertEquals(expected = project, actual = workspace.session.value?.root)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "opened-rule"))
    }

    @Test
    fun `discard on new project clears everything`() {
        val state = scratchState()
        val workspace = ProjectWorkspace(state = state)

        workspace.newProject()
        workspace.onUnsavedChangesDiscard(pending = PendingProjectAction.NewProject)

        assertNull(actual = workspace.dialog.value)
        assertNull(actual = workspace.session.value)
        assertEquals(expected = "", actual = state.ruleValue.value.text)
    }

    @Test
    fun `discard on close lets the window go`() {
        val workspace = ProjectWorkspace(state = scratchState())

        workspace.requestClose()
        workspace.onUnsavedChangesDiscard(pending = PendingProjectAction.CloseWindow)

        assertTrue(actual = workspace.closeRequested.value)
    }

    /** Save must both write the project and then carry out the action that was interrupted. */
    @Test
    fun `save then resume saves and opens`() {
        val state = scratchState()
        val destination = Files.createTempDirectory("saved-project")
        val project = createProject(ruleId = "opened-rule")
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { project.resolve("manifest.yaml") },
            chooseManifestToSave = { destination.resolve("manifest.yaml") },
        )

        workspace.openProject()
        workspace.onUnsavedChangesSave(pending = PendingProjectAction.OpenProject)

        assertNull(actual = workspace.dialog.value)
        assertTrue(actual = Files.exists(destination.resolve("manifest.yaml")))
        assertTrue(actual = Files.exists(destination.resolve("rules/scratch-rule.rule")))
        assertEquals(expected = project, actual = workspace.session.value?.root)
    }

    /** A cancelled save must not go on to discard the work it failed to save. */
    @Test
    fun `cancelling the save cancels the interrupted action`() {
        val state = scratchState()
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { fail() },
            chooseManifestToSave = { null },
        )

        workspace.openProject()
        workspace.onUnsavedChangesSave(pending = PendingProjectAction.OpenProject)

        assertNull(actual = workspace.dialog.value)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "scratch-rule"))
    }

    @Test
    fun `cancel leaves everything as it was`() {
        val state = scratchState()
        val workspace = ProjectWorkspace(state = state, chooseManifestToOpen = { fail() })

        workspace.openProject()
        workspace.dismissDialog()

        assertNull(actual = workspace.dialog.value)
        assertNull(actual = workspace.session.value)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "scratch-rule"))
    }

    @Test
    fun `a clean project opens without asking`() {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        val project = createProject(ruleId = "opened-rule")
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { project.resolve("manifest.yaml") },
        )

        workspace.openProject()

        assertNull(actual = workspace.dialog.value)
        assertEquals(expected = project, actual = workspace.session.value?.root)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun fail(): Path = throw AssertionError("the file dialog should not have been opened")

    private fun scratchState(): RuleEditorState {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        state.ruleValue.value = TextFieldValue(text = ruleText(ruleId = "scratch-rule"))
        return state
    }

    private fun createProject(ruleId: String): Path {
        val root = Files.createTempDirectory("workspace-project")
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(root.resolve("rules/main.rule"), ruleText(ruleId = ruleId))
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                entries:
                  - id: default
                    rules:
                      - rules/main.rule
            """.trimIndent(),
        )
        return root
    }

    private fun ruleText(ruleId: String): String = """
        rule "$ruleId" {
          when purpose equals "rent"
          then label "rent"
        }
    """.trimIndent()
}
