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
import kotlin.test.assertTrue

class ProjectSaverTest {

    @Test
    fun `saving a scratch project creates the whole tree`() {
        val root = Files.createTempDirectory("new-project")
        val state = newState(ruleId = "discount")
        val saver = ProjectSaver(dirtyState = ProjectDirtyState())

        val outcome = saver.save(state = state, session = scratchSession(root = root))

        val saved = assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertTrue(actual = Files.isDirectory(root.resolve("rules")))
        assertTrue(actual = Files.isDirectory(root.resolve("schemas")))
        assertTrue(actual = Files.exists(root.resolve("manifest.yaml")))
        assertTrue(actual = Files.exists(root.resolve("schemas/schema.yaml")))
        assertTrue(actual = Files.exists(root.resolve("schemas/actions.yaml")))
        // The rule file is named after the rule the user wrote, not a placeholder.
        assertTrue(actual = Files.exists(root.resolve("rules/discount.rule")))
        assertEquals(expected = listOf("rules/discount.rule"), actual = saved.session.ruleFiles)
    }

    /** The manifest must describe what is actually on disk — that is the point of saving as a project. */
    @Test
    fun `save then load round trips`() {
        val root = Files.createTempDirectory("round-trip")
        val state = newState(ruleId = "discount")
        val dirtyState = ProjectDirtyState()

        ProjectSaver(dirtyState = dirtyState).save(state = state, session = scratchSession(root = root))

        val reopened = newState(ruleId = "ignored")
        val result = ProjectLoader(dirtyState = ProjectDirtyState()).load(
            manifestPath = root.resolve("manifest.yaml"),
            into = reopened,
        )

        val loaded = assertIs<ProjectLoadResult.Loaded>(value = result)
        assertTrue(actual = loaded.session.missingFiles.isEmpty())
        assertEquals(expected = "schemas/schema.yaml", actual = loaded.session.schemaLink)
        assertEquals(expected = listOf("rules/discount.rule"), actual = loaded.session.ruleFiles)
        assertTrue(actual = reopened.ruleValue.value.text.contains(other = "discount"))
        assertTrue(actual = reopened.schemaText.value.contains(other = "purpose"))
    }

    /** A shared schema is never written without the user agreeing to change it for everyone. */
    @Test
    fun `external schema needs confirmation before it is written`() {
        val parent = Files.createTempDirectory("shared-workspace")
        val shared = Files.createDirectories(parent.resolve("shared"))
        val sharedSchema = shared.resolve("common.yaml")
        Files.writeString(sharedSchema, schemaYaml())
        val root = Files.createDirectories(parent.resolve("project"))

        val state = newState(ruleId = "discount")
        state.schemaText.value = schemaYaml() + "\n# locally edited"
        val session = scratchSession(root = root).copy(schemaLink = "../shared/common.yaml")

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).save(state = state, session = session)

        val needed = assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = outcome)
        val request = assertIs<ProjectSaveConfirmation.ExternalWrite>(value = needed.request)
        assertEquals(expected = ProjectFileKind.SCHEMA, actual = request.kind)
        assertEquals(expected = schemaYaml(), actual = Files.readString(sharedSchema))
    }

    @Test
    fun `approved external write reaches the shared file`() {
        val parent = Files.createTempDirectory("shared-workspace")
        val shared = Files.createDirectories(parent.resolve("shared"))
        val sharedSchema = shared.resolve("common.yaml")
        Files.writeString(sharedSchema, schemaYaml())
        val root = Files.createDirectories(parent.resolve("project"))

        val state = newState(ruleId = "discount")
        state.schemaText.value = schemaYaml() + "\n# locally edited"

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).save(
            state = state,
            session = scratchSession(root = root).copy(schemaLink = "../shared/common.yaml"),
            approvals = ProjectSaveApprovals(externalWrites = setOf(ProjectFileKind.SCHEMA)),
        )

        assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertTrue(actual = Files.readString(sharedSchema).contains(other = "locally edited"))
    }

    /** A file changed underneath the editor must not be silently overwritten. */
    @Test
    fun `disk conflict needs confirmation`() {
        val root = Files.createTempDirectory("conflict")
        val state = newState(ruleId = "discount")
        val dirtyState = ProjectDirtyState()
        val saver = ProjectSaver(dirtyState = dirtyState)

        val first = assertIs<ProjectSaveOutcome.Saved>(
            value = saver.save(state = state, session = scratchSession(root = root)),
        )

        // Someone else edits the rule file, then the user edits their buffer and saves.
        Files.writeString(root.resolve("rules/discount.rule"), "rule \"other\" { when purpose equals \"x\" then label \"x\" }")
        state.ruleValue.value = TextFieldValue(text = ruleText(ruleId = "discount") + "\n// mine")

        val outcome = saver.save(state = state, session = first.session)

        val needed = assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = outcome)
        assertIs<ProjectSaveConfirmation.DiskConflict>(value = needed.request)
    }

    @Test
    fun `save as copies the project and rewrites external links`() {
        val parent = Files.createTempDirectory("shared-workspace")
        val shared = Files.createDirectories(parent.resolve("shared"))
        Files.writeString(shared.resolve("common.yaml"), schemaYaml())

        val origin = Files.createDirectories(parent.resolve("origin"))
        val state = newState(ruleId = "discount")
        val saver = ProjectSaver(dirtyState = ProjectDirtyState())
        val saved = assertIs<ProjectSaveOutcome.Saved>(
            value = saver.save(
                state = state,
                session = scratchSession(root = origin).copy(schemaLink = "../shared/common.yaml"),
                approvals = ProjectSaveApprovals(externalWrites = setOf(ProjectFileKind.SCHEMA)),
            ),
        )

        val target = Files.createDirectories(parent.resolve("nested").resolve("copy"))
        val outcome = saver.saveAs(
            state = state,
            session = saved.session,
            newManifestPath = target.resolve("manifest.yaml"),
            approvals = ProjectSaveApprovals(externalWrites = setOf(ProjectFileKind.SCHEMA)),
        )

        val copied = assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertEquals(expected = target, actual = copied.session.root)
        assertTrue(actual = Files.exists(target.resolve("rules/discount.rule")))
        // One directory deeper, so the shared schema needs one more `..` to stay pointed at it.
        assertEquals(expected = "../../shared/common.yaml", actual = copied.session.schemaLink)
        assertTrue(actual = Files.exists(origin.resolve("rules/discount.rule")))
    }

    @Test
    fun `multi entry project refuses a plain save`() {
        val root = Files.createTempDirectory("multi")
        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).save(
            state = newState(ruleId = "discount"),
            session = scratchSession(root = root).copy(isMultiEntry = true),
        )

        assertIs<ProjectSaveOutcome.Failed>(value = outcome)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun scratchSession(root: Path) = ProjectSession(
        root = root,
        manifestFileName = "manifest.yaml",
        entryId = "default",
    )

    private fun newState(ruleId: String): RuleEditorState {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        state.ruleValue.value = TextFieldValue(text = ruleText(ruleId = ruleId))
        state.schemaText.value = schemaYaml()
        state.actionSchemaText.value = actionsYaml()
        return state
    }

    private fun schemaYaml(): String = """
        schema: sample
        fields:
          purpose:
            type: text
            normalizers:
              - trim
            operators:
              - equals
    """.trimIndent()

    private fun actionsYaml(): String = """
        actions:
          label:
            argTypes: [string]
    """.trimIndent()

    private fun ruleText(ruleId: String): String = """
        rule "$ruleId" {
          when purpose equals "rent"
          then label "rent"
        }
    """.trimIndent()
}
