package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.project.model.ProjectEntry
import ui.project.model.ProjectSession
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loading a sample replaces the project, not just the buffers.
 *
 * The sample path used to write only `RuleEditorState`, so a sample loaded over an open project left
 * the session — and the dirty baselines — describing that project. Visibly, the top bar went on naming
 * its entry. Less visibly, it stayed the save target: the next Save wrote the sample's schema and
 * actions into its directory and rewrote its manifest.
 */
class ProjectWorkspaceSampleTest {

    @Test
    fun `loading a sample clears the session of the project that was open`() {
        val state = cleanState()
        val project = createProject()
        val workspace = workspaceFor(state = state, project = project)

        workspace.openProject()
        assertNotNull(actual = workspace.session.value, message = "the project has to be open to begin with")

        workspace.loadSample { state.reset() }

        assertNull(
            actual = workspace.session.value,
            message = "a sample has no location on disk, so it has no session — and the picker renders " +
                    "only under a non-null session, which is what makes the stale entry impossible",
        )
    }

    /**
     * Ordering the KDoc on `loadSample` claims: the clearing happens first, so the editor write cannot
     * observe — or be observed against — the project being replaced.
     */
    @Test
    fun `the editor write runs after the session is cleared`() {
        val state = cleanState()
        val workspace = workspaceFor(state = state, project = createProject())
        workspace.openProject()

        var sessionSeenByTheWrite: ProjectSession? = ProjectSession.singleEntry(
            root = Path.of("/tmp/sentinel"),
            manifestFileName = "manifest.yaml",
            entry = ProjectEntry(id = "sentinel"),
        )
        workspace.loadSample { sessionSeenByTheWrite = workspace.session.value }

        assertNull(actual = sessionSeenByTheWrite)
    }

    /**
     * Dirtiness is a comparison against the last thing read from disk. Left alone, the sample's buffers
     * are compared against the *project's* baselines, which makes "unsaved" a statement about the wrong
     * files — and enables a Save that had nowhere safe to go.
     */
    @Test
    fun `loading a sample re-baselines dirtiness`() {
        val state = cleanState()
        val workspace = workspaceFor(state = state, project = createProject())

        workspace.openProject()
        assertFalse(actual = workspace.isDirty, message = "a freshly opened project is clean")

        // Empty buffers: dirty against the project's baselines, clean against none.
        workspace.loadSample { state.reset() }

        assertFalse(
            actual = workspace.isDirty,
            message = "the project's baselines are gone, so empty buffers are not 'edited away from' them",
        )
    }

    /** The data-loss regression: a sample's buffers must never be written into the open project. */
    @Test
    fun `saving a sample asks for a location and leaves the project untouched`() {
        val state = cleanState()
        val project = createProject()
        val savedTo = Files.createTempDirectory("sample-saved-elsewhere")
        var askedWhereToSave = false

        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { project.resolve("manifest.yaml") },
            chooseManifestToSave = {
                askedWhereToSave = true
                savedTo.resolve("manifest.yaml")
            },
        )

        workspace.openProject()
        workspace.loadSample {
            state.reset()
            state.schemaText.value = SAMPLE_SCHEMA
            state.ruleValue.value = TextFieldValue(text = ruleText(ruleId = "sample-rule"))
        }
        workspace.saveProject()

        assertTrue(
            actual = askedWhereToSave,
            message = "with no session the save has to ask, not fall back to the project that was open",
        )
        assertEquals(
            expected = PROJECT_SCHEMA,
            actual = Files.readString(project.resolve("schema.yaml")),
            message = "the opened project's schema must not have been overwritten by the sample's",
        )
    }

    /**
     * The belt-and-braces half. `loadSample` clears the session, so this state is unreachable through
     * the public API today — the flag exists so that a session arriving from anywhere else cannot
     * become the save target for buffers that never came out of it.
     */
    @Test
    fun `a session appearing after a sample load is not used as the save target`() {
        val state = cleanState()
        val project = createProject()
        var askedWhereToSave = false
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { project.resolve("manifest.yaml") },
            chooseManifestToSave = {
                askedWhereToSave = true
                null // cancels, which is enough: the question is which target was chosen
            },
        )

        workspace.loadSample { state.schemaText.value = SAMPLE_SCHEMA }
        workspace.session.value = ProjectSession.singleEntry(
            root = project,
            manifestFileName = "manifest.yaml",
            entry = ProjectEntry(id = "default"),
        )
        workspace.saveProject()

        assertTrue(
            actual = askedWhereToSave,
            message = "the sample's buffers did not come from that session, so it is not their home",
        )
        assertEquals(
            expected = PROJECT_SCHEMA,
            actual = Files.readString(project.resolve("schema.yaml")),
        )
    }

    /** Opening a project makes its own files the buffers' home again, so the guard must let go. */
    @Test
    fun `opening a project after a sample restores it as the save target`() {
        val state = cleanState()
        val project = createProject()
        val workspace = ProjectWorkspace(
            state = state,
            chooseManifestToOpen = { project.resolve("manifest.yaml") },
            chooseManifestToSave = { throw AssertionError("an opened project already knows where it lives") },
        )

        workspace.loadSample { state.reset() }
        workspace.openProject()
        workspace.saveProject()

        assertEquals(expected = project, actual = workspace.session.value?.root)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun cleanState() = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))

    private fun workspaceFor(state: RuleEditorState, project: Path) = ProjectWorkspace(
        state = state,
        chooseManifestToOpen = { project.resolve("manifest.yaml") },
        chooseManifestToSave = { throw AssertionError("the save dialog should not have been opened") },
    )

    private fun createProject(): Path {
        val root = Files.createTempDirectory("workspace-project")
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(root.resolve("rules/main.rule"), ruleText(ruleId = "project-rule"))
        Files.writeString(root.resolve("schema.yaml"), PROJECT_SCHEMA)
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                entries:
                  - id: default
                    schema: schema.yaml
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

    private companion object {
        val PROJECT_SCHEMA: String = """
            schema: project
            fields:
              purpose:
                type: text
                operators: [equals]
        """.trimIndent()

        val SAMPLE_SCHEMA: String = """
            schema: sample
            fields:
              amount:
                type: decimal
                operators: [gte]
        """.trimIndent()
    }
}
