package ui.project

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.project.io.ProjectLoader
import ui.project.io.ProjectSaver
import ui.project.model.ProjectEntry
import ui.project.model.ProjectFileKind
import ui.project.model.ProjectSession
import ui.project.model.dialog.ProjectSaveApprovals
import ui.project.model.dialog.ProjectSaveConfirmation
import ui.project.model.io.ProjectLoadResult
import ui.project.model.io.ProjectSaveOutcome
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
        val session = scratchSession(root = root).withActive { it.copy(schemaLink = "../shared/common.yaml") }

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
            session = scratchSession(root = root).withActive { it.copy(schemaLink = "../shared/common.yaml") },
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
        Files.writeString(
            root.resolve("rules/discount.rule"),
            "rule \"other\" { when purpose equals \"x\" then label \"x\" }",
        )
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
                session = scratchSession(root = origin).withActive { it.copy(schemaLink = "../shared/common.yaml") },
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

    /**
     * The entries the user is not looking at must survive a save.
     *
     * Only the active entry has buffers behind it, so the others exist purely in the session — if the
     * saver rebuilt the manifest from the buffers alone they would be silently deleted from a project
     * the user only meant to edit one part of.
     */
    @Test
    fun `saving a multi entry project keeps every entry and leaves the inactive files alone`() {
        val root = Files.createTempDirectory("multi")
        Files.createDirectories(root.resolve("rules"))
        val otherRule = root.resolve("rules/other.rule")
        Files.writeString(otherRule, ruleText(ruleId = "untouched"))

        val session = ProjectSession(
            root = root,
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "primary"),
                ProjectEntry(
                    id = "secondary",
                    schemaLink = "schemas/other.yaml",
                    ruleFiles = listOf("rules/other.rule"),
                ),
            ),
            activeEntryId = "primary",
        )

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).save(
            state = newState(ruleId = "discount"),
            session = session,
        )

        val saved = assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertEquals(expected = listOf("primary", "secondary"), actual = saved.session.entries.map { it.id })

        val manifest = Files.readString(root.resolve("manifest.yaml"))
        assertTrue(actual = manifest.contains(other = "id: primary"))
        assertTrue(actual = manifest.contains(other = "id: secondary"))
        assertTrue(actual = manifest.contains(other = "rules/other.rule"))
        assertEquals(expected = ruleText(ruleId = "untouched"), actual = Files.readString(otherRule))
    }

    /** Two entries defaulting to the same `schemas/schema.yaml` would have one overwrite the other. */
    @Test
    fun `a new file in a multi entry project is named after its entry`() {
        val root = Files.createTempDirectory("multi-names")
        val session = ProjectSession(
            root = root,
            manifestFileName = "manifest.yaml",
            entries = listOf(ProjectEntry(id = "risk"), ProjectEntry(id = "other")),
            activeEntryId = "risk",
        )

        ProjectSaver(dirtyState = ProjectDirtyState()).save(state = newState(ruleId = "discount"), session = session)

        assertTrue(actual = Files.exists(root.resolve("schemas/risk-schema.yaml")))
        assertTrue(actual = Files.exists(root.resolve("schemas/risk-actions.yaml")))
        assertTrue(actual = Files.exists(root.resolve("rules/risk/discount.rule")))
    }

    @Test
    fun `save as copies the rule files of every entry`() {
        val parent = Files.createTempDirectory("copy-all")
        val origin = Files.createDirectories(parent.resolve("origin"))
        Files.createDirectories(origin.resolve("rules"))
        Files.writeString(origin.resolve("rules/other.rule"), ruleText(ruleId = "untouched"))

        val session = ProjectSession(
            root = origin,
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "primary"),
                ProjectEntry(id = "secondary", ruleFiles = listOf("rules/other.rule")),
            ),
            activeEntryId = "primary",
        )

        val target = Files.createDirectories(parent.resolve("copy"))
        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).saveAs(
            state = newState(ruleId = "discount"),
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
        )

        assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertTrue(actual = Files.exists(target.resolve("rules/other.rule")))
    }

    @Test
    fun `save as asks before replacing a different file already in the target folder`() {
        val parent = Files.createTempDirectory("save-as-clash")
        val origin = Files.createDirectories(parent.resolve("origin/rules")).parent
        Files.writeString(origin.resolve("rules/pricing.rule"), ruleText(ruleId = "ours"))
        val target = Files.createDirectories(parent.resolve("other/rules")).parent
        val theirs = ruleText(ruleId = "theirs")
        Files.writeString(target.resolve("rules/pricing.rule"), theirs)
        val session = ProjectSession(
            root = origin,
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "primary"),
                ProjectEntry(id = "secondary", ruleFiles = listOf("rules/pricing.rule")),
            ),
            activeEntryId = "primary",
        )
        val saver = ProjectSaver(dirtyState = ProjectDirtyState())

        val first = saver.saveAs(
            state = newState(ruleId = "discount"),
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
        )

        val needed = assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = first)
        assertEquals(
            expected = ProjectSaveConfirmation.DiskConflict(
                kind = ProjectFileKind.RULE,
                relativePath = "rules/pricing.rule",
            ),
            actual = needed.request,
        )
        assertEquals(expected = theirs, actual = Files.readString(target.resolve("rules/pricing.rule")))
        assertTrue(actual = Files.notExists(target.resolve("manifest.yaml")))

        val approved = saver.saveAs(
            state = newState(ruleId = "discount"),
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
            approvals = ProjectSaveApprovals(overwrittenConflicts = setOf("rules/pricing.rule")),
        )

        assertIs<ProjectSaveOutcome.Saved>(value = approved)
        assertEquals(
            expected = ruleText(ruleId = "ours"),
            actual = Files.readString(target.resolve("rules/pricing.rule")),
        )
    }

    @Test
    fun `a save waiting for an answer creates no folders`() {
        val parent = Files.createTempDirectory("save-pending-folders")
        Files.writeString(Files.createDirectories(parent.resolve("shared")).resolve("common.yaml"), schemaYaml())
        val root = Files.createDirectories(parent.resolve("project"))
        val state = newState(ruleId = "discount")
        state.schemaText.value = schemaYaml() + "\n# edited\n"

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).save(
            state = state,
            session = scratchSession(root = root).withActive { it.copy(schemaLink = "../shared/common.yaml") },
        )

        assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = outcome)
        assertTrue(actual = Files.notExists(root.resolve("rules")))
        assertTrue(actual = Files.notExists(root.resolve("schemas")))
    }

    /** `eu` and `EU` slug to the same default schema path; the second save must not replace the first. */
    @Test
    fun `saving onto a file this session never read needs confirmation`() {
        val root = Files.createTempDirectory("unread-default")
        Files.createDirectories(root.resolve("schemas"))
        val other = "schema: other\nfields: {}\n"
        Files.writeString(root.resolve("schemas/eu-schema.yaml"), other)
        val session = ProjectSession(
            root = root,
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "eu", schemaLink = "schemas/eu-schema.yaml"),
                ProjectEntry(id = "EU"),
            ),
            activeEntryId = "EU",
        )

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).save(
            state = newState(ruleId = "x"),
            session = session,
        )

        val needed = assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = outcome)
        assertEquals(
            expected = ProjectSaveConfirmation.DiskConflict(
                kind = ProjectFileKind.SCHEMA,
                relativePath = "schemas/eu-schema.yaml",
            ),
            actual = needed.request,
        )
        assertEquals(expected = other, actual = Files.readString(root.resolve("schemas/eu-schema.yaml")))
    }

    @Test
    fun `save as re-points a rule file linked from outside the project`() {
        val parent = Files.createTempDirectory("save-as-external-rule")
        val shared = Files.createDirectories(parent.resolve("shared"))
        val sharedRule = ruleText(ruleId = "shared")
        Files.writeString(shared.resolve("x.rule"), sharedRule)
        val origin = Files.createDirectories(parent.resolve("origin"))
        val session = ProjectSession.singleEntry(
            root = origin,
            manifestFileName = "manifest.yaml",
            entry = ProjectEntry(id = "default", ruleFiles = listOf("../shared/x.rule")),
        )
        val state = newState(ruleId = "shared")
        state.selectedManifestRuleFile.value = "../shared/x.rule"
        state.inMemoryRuleFiles.value = mapOf("../shared/x.rule" to sharedRule)
        val target = Files.createDirectories(parent.resolve("nested").resolve("copy"))

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).saveAs(
            state = state,
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
        )

        val saved = assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertEquals(expected = listOf("../../shared/x.rule"), actual = saved.session.ruleFiles)
        assertEquals(expected = "../../shared/x.rule", actual = state.selectedManifestRuleFile.value)
        assertEquals(expected = sharedRule, actual = Files.readString(shared.resolve("x.rule")))
    }

    @Test
    fun `save as waiting for an answer copies nothing and keeps the old baselines`() {
        val parent = Files.createTempDirectory("save-as-pending")
        Files.writeString(Files.createDirectories(parent.resolve("shared")).resolve("common.yaml"), schemaYaml())
        val origin = Files.createDirectories(parent.resolve("origin"))
        val dirtyState = ProjectDirtyState()
        val saver = ProjectSaver(dirtyState = dirtyState)
        val state = newState(ruleId = "discount")
        val saved = assertIs<ProjectSaveOutcome.Saved>(
            value = saver.save(
                state = state,
                session = scratchSession(root = origin).withActive { it.copy(schemaLink = "../shared/common.yaml") },
                approvals = ProjectSaveApprovals(externalWrites = setOf(ProjectFileKind.SCHEMA)),
            ),
        )
        val baselines = dirtyState.snapshot()
        // An edited shared schema: writing it leaves the project, so the save stops to ask.
        state.schemaText.value = schemaYaml() + "\n# edited\n"
        val target = Files.createDirectories(parent.resolve("copy"))

        val outcome = saver.saveAs(
            state = state,
            session = saved.session,
            newManifestPath = target.resolve("manifest.yaml"),
        )

        val needed = assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = outcome)
        assertIs<ProjectSaveConfirmation.ExternalWrite>(value = needed.request)
        assertTrue(actual = Files.notExists(target.resolve("rules")))
        assertTrue(actual = Files.notExists(target.resolve("schemas")))
        assertEquals(expected = baselines, actual = dirtyState.snapshot())
    }

    @Test
    fun `save as that fails while writing restores the buffers and baselines`() {
        val parent = Files.createTempDirectory("save-as-throws")
        val sharedRule = ruleText(ruleId = "shared")
        Files.writeString(Files.createDirectories(parent.resolve("shared")).resolve("x.rule"), sharedRule)
        val origin = Files.createDirectories(parent.resolve("origin"))
        val session = ProjectSession.singleEntry(
            root = origin,
            manifestFileName = "manifest.yaml",
            entry = ProjectEntry(id = "default", ruleFiles = listOf("../shared/x.rule")),
        )
        val state = newState(ruleId = "shared")
        state.selectedManifestRuleFile.value = "../shared/x.rule"
        state.inMemoryRuleFiles.value = mapOf("../shared/x.rule" to sharedRule)
        val dirtyState = ProjectDirtyState()
        dirtyState.markClean(key = ProjectDirtyState.ruleKey(relativePath = "../shared/x.rule"), content = sharedRule)
        val baselines = dirtyState.snapshot()
        val target = Files.createDirectories(parent.resolve("nested").resolve("copy"))
        // A non-empty directory where the manifest goes: the last write of the save throws.
        Files.writeString(Files.createDirectories(target.resolve("manifest.yaml")).resolve("keep.txt"), "x")

        val outcome = ProjectSaver(dirtyState = dirtyState).saveAs(
            state = state,
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
        )

        assertIs<ProjectSaveOutcome.Failed>(value = outcome)
        assertEquals(expected = "../shared/x.rule", actual = state.selectedManifestRuleFile.value)
        assertEquals(expected = setOf("../shared/x.rule"), actual = state.inMemoryRuleFiles.value.keys)
        assertEquals(expected = baselines, actual = dirtyState.snapshot())
    }

    @Test
    fun `save as copies the schema and actions of every entry`() {
        val parent = Files.createTempDirectory("save-as-schemas")
        val origin = Files.createDirectories(parent.resolve("origin"))
        Files.createDirectories(origin.resolve("schemas"))
        Files.writeString(origin.resolve("schemas/second-schema.yaml"), schemaYaml())
        Files.writeString(origin.resolve("schemas/second-actions.yaml"), actionsYaml())
        val session = ProjectSession(
            root = origin,
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "first"),
                ProjectEntry(
                    id = "second",
                    schemaLink = "schemas/second-schema.yaml",
                    actionsLink = "schemas/second-actions.yaml",
                ),
            ),
            activeEntryId = "first",
        )
        val target = Files.createDirectories(parent.resolve("copy"))

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).saveAs(
            state = newState(ruleId = "discount"),
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
        )

        assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertEquals(expected = schemaYaml(), actual = Files.readString(target.resolve("schemas/second-schema.yaml")))
        assertEquals(expected = actionsYaml(), actual = Files.readString(target.resolve("schemas/second-actions.yaml")))
    }

    @Test
    fun `save as asks before replacing a shared rule file that changed on disk`() {
        val parent = Files.createTempDirectory("save-as-shared-changed")
        val shared = Files.createDirectories(parent.resolve("shared"))
        val readVersion = ruleText(ruleId = "shared")
        Files.writeString(shared.resolve("x.rule"), readVersion)
        val origin = Files.createDirectories(parent.resolve("origin"))
        val session = ProjectSession.singleEntry(
            root = origin,
            manifestFileName = "manifest.yaml",
            entry = ProjectEntry(id = "default", ruleFiles = listOf("../shared/x.rule")),
        )
        val state = newState(ruleId = "shared")
        state.selectedManifestRuleFile.value = "../shared/x.rule"
        state.inMemoryRuleFiles.value = mapOf("../shared/x.rule" to readVersion)
        val dirtyState = ProjectDirtyState()
        dirtyState.markClean(key = ProjectDirtyState.ruleKey(relativePath = "../shared/x.rule"), content = readVersion)
        val teammateVersion = ruleText(ruleId = "teammate")
        Files.writeString(shared.resolve("x.rule"), teammateVersion)
        val target = Files.createDirectories(parent.resolve("nested").resolve("copy"))

        val outcome = ProjectSaver(dirtyState = dirtyState).saveAs(
            state = state,
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
            approvals = ProjectSaveApprovals(externalWrites = setOf(ProjectFileKind.RULE)),
        )

        val needed = assertIs<ProjectSaveOutcome.NeedsConfirmation>(value = outcome)
        assertIs<ProjectSaveConfirmation.DiskConflict>(value = needed.request)
        assertEquals(expected = teammateVersion, actual = Files.readString(shared.resolve("x.rule")))
    }

    @Test
    fun `save as writes a copy through a symlink already at the target`() {
        val parent = Files.createTempDirectory("save-as-symlink")
        val origin = Files.createDirectories(parent.resolve("origin/rules")).parent
        Files.writeString(origin.resolve("rules/pricing.rule"), ruleText(ruleId = "ours"))
        val elsewhere = Files.writeString(parent.resolve("elsewhere.rule"), ruleText(ruleId = "theirs"))
        val target = Files.createDirectories(parent.resolve("copy/rules")).parent
        val link = Files.createSymbolicLink(target.resolve("rules/pricing.rule"), elsewhere)
        val session = ProjectSession(
            root = origin,
            manifestFileName = "manifest.yaml",
            entries = listOf(
                ProjectEntry(id = "primary"),
                ProjectEntry(id = "secondary", ruleFiles = listOf("rules/pricing.rule")),
            ),
            activeEntryId = "primary",
        )

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).saveAs(
            state = newState(ruleId = "discount"),
            session = session,
            newManifestPath = target.resolve("manifest.yaml"),
            approvals = ProjectSaveApprovals(overwrittenConflicts = setOf("rules/pricing.rule")),
        )

        assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertTrue(actual = Files.isSymbolicLink(link))
        assertEquals(expected = ruleText(ruleId = "ours"), actual = Files.readString(elsewhere))
    }

    /** Writing the index alone, for when files were just deleted and it must stop naming them. */
    @Test
    fun `save manifest writes only the manifest`() {
        val root = Files.createTempDirectory("manifest-only")
        val session = ProjectSession(
            root = root,
            manifestFileName = "manifest.yaml",
            entries = listOf(ProjectEntry(id = "only")),
            activeEntryId = "only",
        )

        val outcome = ProjectSaver(dirtyState = ProjectDirtyState()).saveManifest(session = session)

        assertIs<ProjectSaveOutcome.Saved>(value = outcome)
        assertTrue(actual = Files.readString(root.resolve("manifest.yaml")).contains(other = "id: only"))
        assertTrue(actual = Files.notExists(root.resolve("schemas/schema.yaml")))
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun scratchSession(root: Path): ProjectSession {
        return ProjectSession.singleEntry(
            root = root,
            manifestFileName = "manifest.yaml",
            entry = ProjectEntry(id = "default"),
        )
    }

    private fun newState(ruleId: String): RuleEditorState {
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        state.ruleValue.value = TextFieldValue(text = ruleText(ruleId = ruleId))
        state.schemaText.value = schemaYaml()
        state.actionSchemaText.value = actionsYaml()
        return state
    }

    private fun schemaYaml(): String {
        return """
            schema: sample
            fields:
              purpose:
                type: text
                normalizers:
                  - trim
                operators:
                  - equals
        """.trimIndent()
    }

    private fun actionsYaml(): String {
        return """
            actions:
              label:
                argTypes: [string]
        """.trimIndent()
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
