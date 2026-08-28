package ui.project

import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.manifest.model.ManifestEditorState
import ui.project.manifest.toEditorState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The invariant nothing asserted, which is why four desyncs survived:
 * `session.activeEntryId == state.selectedManifestEntry`.
 *
 * There are three separate notions of "the selected entry" and nothing observes them into agreement —
 * the session's is the save target, the editor's is what every read of the parsed manifest goes through.
 * When they disagree, `currentEntryRulePaths()` looks up an id the manifest no longer has, finds nothing,
 * and the next `loadRuleFiles` writes an empty map over the working copy. That is silent data loss, not a
 * cosmetic drift, which is what makes this worth a test of its own.
 */
class ManifestSessionSyncTest {

    private fun openedWorkspace(): Pair<ProjectWorkspace, RuleEditorState> {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        val root = createProject()
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { root.resolve("manifest.yaml") },
        )
        workspace.openProject()
        return workspace to state
    }

    private fun assertInStep(workspace: ProjectWorkspace, state: RuleEditorState, after: String) {
        assertEquals(
            expected = workspace.session.value?.activeEntryId,
            actual = state.selectedManifestEntry.value,
            message = "the session and the editor disagree about the active entry after $after",
        )
    }

    @Test
    fun `opening a project puts the two in step`() {
        val (workspace, state) = openedWorkspace()

        assertEquals(expected = "default", actual = state.selectedManifestEntry.value)
        assertInStep(workspace = workspace, state = state, after = "open")
    }

    /**
     * The case that lost data. Renaming the active entry leaves every path alone, so nothing reloads —
     * and the editor's selection used to keep pointing at the id that no longer exists.
     */
    @Test
    fun `renaming the active entry keeps the two in step`() {
        val (workspace, state) = openedWorkspace()
        val before = state.inMemoryRuleFiles.value

        workspace.applyManifestEditorState(edited = renamed(workspace = workspace, to = "renamed"))

        assertEquals(expected = "renamed", actual = workspace.session.value?.activeEntryId)
        assertInStep(workspace = workspace, state = state, after = "a rename")
        assertTrue(
            actual = before.isNotEmpty(),
            message = "the fixture loaded no rule files, so this test would prove nothing",
        )
    }

    /** And the consequence, stated as the consequence: the working copy survives the rename. */
    @Test
    fun `renaming the active entry does not empty the working copy`() {
        val (workspace, state) = openedWorkspace()
        val before = state.inMemoryRuleFiles.value

        workspace.applyManifestEditorState(edited = renamed(workspace = workspace, to = "renamed"))
        // What the rule tree and every entry-wide action go through.
        state.loadRuleFiles(relativePaths = state.currentEntryRulePathsForTest())

        assertEquals(expected = before.keys, actual = state.inMemoryRuleFiles.value.keys)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "opened-rule"))
    }

    @Test
    fun `adding an entry and switching to it keeps the two in step`() {
        val (workspace, state) = openedWorkspace()

        workspace.addEntry(entryId = "second")

        assertEquals(expected = "second", actual = workspace.session.value?.activeEntryId)
        assertInStep(workspace = workspace, state = state, after = "adding an entry")
    }

    @Test
    fun `switching back to the first entry keeps the two in step`() {
        val (workspace, state) = openedWorkspace()
        workspace.addEntry(entryId = "second")

        workspace.selectEntry(entryId = "default")

        assertInStep(workspace = workspace, state = state, after = "switching entry")
        assertEquals(expected = "default", actual = state.selectedManifestEntry.value)
    }

    /** Editing something other than the id must not disturb the pair either. */
    @Test
    fun `editing the manifest name keeps the two in step`() {
        val (workspace, state) = openedWorkspace()
        val current = workspace.session.value!!.toEditorState()

        workspace.applyManifestEditorState(edited = current.copy(name = "renamed-project"))

        assertInStep(workspace = workspace, state = state, after = "renaming the project")
    }

    private fun renamed(workspace: ProjectWorkspace, to: String): ManifestEditorState {
        val current = workspace.session.value!!.toEditorState()
        val active = workspace.session.value!!.activeEntryId
        return current.copy(
            entries = current.entries.map { entry ->
                if (entry.id == active) entry.copy(id = to) else entry
            },
        )
    }

    private fun createProject(): Path {
        val root = Files.createTempDirectory("manifest-sync")
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(
            root.resolve("rules/main.rule"),
            """
                rule "opened-rule" {
                  when purpose equals "rent"
                  then label "rent"
                }
            """.trimIndent(),
        )
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
}

/** Reaches the private lookup the desync corrupted, without widening it for production code. */
private fun RuleEditorState.currentEntryRulePathsForTest(): List<String> =
    parsedManifest.value?.entries
        ?.find { entry -> entry.id == selectedManifestEntry.value }
        ?.rules
        .orEmpty()
