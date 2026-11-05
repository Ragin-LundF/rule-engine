package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.manifest.model.EditableManifestEntry
import ui.project.manifest.toEditorState
import ui.project.model.dialog.PendingProjectAction
import ui.project.model.dialog.ProjectDialog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `a manifest that cannot be written after removal is reported, not claimed as success`() {
        val (workspace, state) = openTwoEntryProject()
        val root = workspace.session.value!!.root
        workspace.requestRemoveEntry(entryId = "second")
        root.toFile().setWritable(false)
        // Skipped where a read-only folder still takes writes (running as root, Windows): kotlin.test has no
        // assumptions, and the write this test needs to fail would succeed there.
        if (Files.isWritable(root)) {
            root.toFile().setWritable(true)
            return
        }

        try {
            workspace.onRemoveEntryKeepingFiles(entryId = "second")
        } finally {
            root.toFile().setWritable(true)
        }

        val dialog = assertIs<ProjectDialog.Error>(value = workspace.dialog.value)
        assertEquals(expected = "Manifest not updated", actual = dialog.title)
        assertFalse(actual = state.status.value.startsWith(prefix = "Removed"))
        assertTrue(actual = Files.readString(root.resolve("manifest.yaml")).contains(other = "id: second"))
        assertTrue(actual = workspace.isDirty, message = "the removal is unsaved work while the manifest names it")
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

    /** A manifest change is unsaved work: Save stays enabled and closing asks, until it is written. */
    @Test
    fun `renaming an entry makes the project dirty until it is saved`() {
        val (workspace, _) = openTwoEntryProject()

        workspace.applyManifestEditorState(
            edited = workspace.session.value!!.toEditorState().let { editorState ->
                editorState.copy(
                    entries = editorState.entries.map { entry ->
                        if (entry.id == "first") entry.copy(id = "renamed") else entry
                    },
                )
            },
        )

        assertTrue(actual = workspace.isDirty, message = "a renamed entry exists only in memory")
        workspace.requestClose()
        assertIs<ProjectDialog.UnsavedChanges>(value = workspace.dialog.value)
        workspace.dismissDialog()

        assertTrue(actual = workspace.saveProject())

        assertFalse(actual = workspace.isDirty, message = "the manifest on disk now has the rename")
        assertTrue(actual = Files.readString(workspace.session.value!!.manifestPath).contains(other = "renamed"))
    }

    @Test
    fun `an added entry is unsaved work`() {
        val (workspace, _) = openTwoEntryProject()

        workspace.addEntry(entryId = "third")

        assertTrue(actual = workspace.isDirty)
    }

    /**
     * The reload reads the manifest again, which activates its first entry. The session has to follow
     * and then go back to the entry that was open — otherwise the buffers hold "first" while the session
     * names "second", and the next save writes the one into the other's files.
     */
    @Test
    fun `reloading after a disk conflict stays on the entry that was open`() {
        val (workspace, state) = openTwoEntryProject()
        workspace.selectEntry(entryId = "second")
        val root = workspace.session.value!!.root
        Files.writeString(root.resolve("rules/second.rule"), ruleText(ruleId = "changed-on-disk"))
        state.ruleValue.value = TextFieldValue(text = state.ruleValue.value.text + "\n# edited")

        assertFalse(actual = workspace.saveProject())
        assertIs<ProjectDialog.DiskConflict>(value = workspace.dialog.value)
        workspace.onReloadConflict()

        assertEquals(expected = "second", actual = workspace.session.value?.activeEntryId)
        assertTrue(
            actual = state.ruleValue.value.text.contains(other = "changed-on-disk"),
            message = "the buffer must hold the reloaded second entry: ${state.ruleValue.value.text}",
        )
        assertFalse(actual = workspace.isDirty)
        assertTrue(actual = Files.readString(root.resolve("rules/main.rule")).contains(other = "alpha-rule"))
    }

    @Test
    fun `a reload that cannot read the manifest says so`() {
        val (workspace, _) = openTwoEntryProject()
        Files.delete(workspace.session.value!!.manifestPath)

        workspace.onReloadConflict()

        val dialog = assertIs<ProjectDialog.Error>(value = workspace.dialog.value)
        assertEquals(expected = "Cannot reload project", actual = dialog.title)
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

    private fun ruleText(ruleId: String): String {
        return """
            rule "$ruleId" {
              when purpose equals "rent"
              then label "rent"
            }
        """.trimIndent()
    }
}
