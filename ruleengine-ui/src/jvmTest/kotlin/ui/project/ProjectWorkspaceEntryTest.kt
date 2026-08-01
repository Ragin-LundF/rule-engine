package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.manifest.EditableManifestEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Managing the entries of an open manifest: switching, adding and removing. */
class ProjectWorkspaceEntryTest {

    @Test
    fun `switching entry swaps the rule buffer`() {
        val (workspace, state) = openTwoEntryProject()

        workspace.selectEntry(entryId = "second")

        assertNull(actual = workspace.dialog.value)
        assertEquals(expected = "second", actual = workspace.session.value?.activeEntryId)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "beta-rule"))
    }

    /** The buffers about to be replaced hold unsaved work, so the switch has to ask first. */
    @Test
    fun `switching with unsaved work asks and resumes after discard`() {
        val (workspace, state) = openTwoEntryProject()
        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        workspace.selectEntry(entryId = "second")

        val dialog = assertIs<ProjectDialog.UnsavedChanges>(value = workspace.dialog.value)
        assertEquals(expected = PendingProjectAction.SwitchEntry(entryId = "second"), actual = dialog.pending)

        workspace.onUnsavedChangesDiscard(pending = dialog.pending)

        assertEquals(expected = "second", actual = workspace.session.value?.activeEntryId)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "beta-rule"))
    }

    @Test
    fun `adding an entry activates it and leaves it empty`() {
        val (workspace, state) = openTwoEntryProject()

        assertTrue(actual = workspace.addEntry(entryId = "third"))

        assertEquals(expected = "third", actual = workspace.session.value?.activeEntryId)
        assertEquals(expected = emptyList(), actual = workspace.session.value?.ruleFiles)
        assertEquals(expected = "", actual = state.ruleValue.value.text)
        // The picker and the rule tree read the parsed manifest, so it has to know the entry exists.
        assertTrue(actual = state.parsedManifest.value?.entries?.any { it.id == "third" } == true)
    }

    @Test
    fun `an entry cannot reuse an existing name`() {
        val (workspace, _) = openTwoEntryProject()

        assertTrue(actual = !workspace.addEntry(entryId = "second"))

        assertIs<ProjectDialog.Error>(value = workspace.dialog.value)
        assertEquals(expected = 2, actual = workspace.session.value?.entries?.size)
    }

    @Test
    fun `an entry cannot be nameless`() {
        val (workspace, _) = openTwoEntryProject()

        assertTrue(actual = !workspace.addEntry(entryId = "   "))

        assertIs<ProjectDialog.Error>(value = workspace.dialog.value)
    }

    @Test
    fun `removing an entry keeping the files rewrites the manifest and leaves them on disk`() {
        val (workspace, _) = openTwoEntryProject()
        val root = workspace.session.value!!.root

        workspace.requestRemoveEntry(entryId = "second")
        val dialog = assertIs<ProjectDialog.RemoveEntry>(value = workspace.dialog.value)
        assertEquals(expected = listOf("rules/second.rule"), actual = dialog.deletable.map { it.relativePath })

        workspace.onRemoveEntryKeepingFiles(entryId = "second")

        assertEquals(expected = listOf("first"), actual = workspace.session.value?.entries?.map { it.id })
        assertTrue(actual = Files.exists(root.resolve("rules/second.rule")))
        assertTrue(actual = !Files.readString(root.resolve("manifest.yaml")).contains(other = "id: second"))
    }

    @Test
    fun `removing an entry deleting the files erases what it owned`() {
        val (workspace, _) = openTwoEntryProject()
        val root = workspace.session.value!!.root

        workspace.requestRemoveEntry(entryId = "second")
        workspace.onRemoveEntryDeletingFiles(entryId = "second")

        assertEquals(expected = listOf("first"), actual = workspace.session.value?.entries?.map { it.id })
        assertTrue(actual = Files.notExists(root.resolve("rules/second.rule")))
        // The other entry's files are untouched — only what the removed entry owned alone goes.
        assertTrue(actual = Files.exists(root.resolve("rules/main.rule")))
    }

    /** Removing the entry being edited has to leave the editor on one that still exists. */
    @Test
    fun `removing the active entry activates another`() {
        val (workspace, state) = openTwoEntryProject()
        workspace.selectEntry(entryId = "second")

        workspace.onRemoveEntryKeepingFiles(entryId = "second")

        assertEquals(expected = "first", actual = workspace.session.value?.activeEntryId)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "alpha-rule"))
    }

    @Test
    fun `the last entry cannot be removed`() {
        val (workspace, _) = openTwoEntryProject()
        workspace.onRemoveEntryKeepingFiles(entryId = "second")

        workspace.requestRemoveEntry(entryId = "first")

        assertIs<ProjectDialog.Error>(value = workspace.dialog.value)
        assertEquals(expected = 1, actual = workspace.session.value?.entries?.size)
    }

    /** Manifest-area edits used to be regenerated over by the saver; they must reach the session. */
    @Test
    fun `manifest editor changes land on the session`() {
        val (workspace, _) = openTwoEntryProject()

        workspace.applyManifestEditorState(
            edited = workspace.session.value!!.toEditorState().let { editorState ->
                editorState.copy(
                    name = "renamed-project",
                    entries = editorState.entries + EditableManifestEntry(id = "added"),
                )
            },
        )

        assertEquals(expected = "renamed-project", actual = workspace.session.value?.manifestName)
        assertEquals(
            expected = listOf("first", "second", "added"),
            actual = workspace.session.value?.entries?.map { it.id },
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun openTwoEntryProject(): Pair<ProjectWorkspace, RuleEditorState> {
        val root = createProject()
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { root.resolve("manifest.yaml") },
        )
        workspace.openProject()
        return workspace to state
    }

    private fun createProject(): Path {
        val root = Files.createTempDirectory("entry-workspace")
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(root.resolve("rules/main.rule"), ruleText(ruleId = "alpha-rule"))
        Files.writeString(root.resolve("rules/second.rule"), ruleText(ruleId = "beta-rule"))
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                name: two-sets
                entries:
                  - id: first
                    rules:
                      - rules/main.rule
                  - id: second
                    rules:
                      - rules/second.rule
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
