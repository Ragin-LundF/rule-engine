package ui.project

import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectLoaderTest {

    @Test
    fun `loads schema actions and rules of the first entry`() {
        val root = createProject(name = "alpha", ruleId = "alpha-rule")
        val state = newState()

        val result = ProjectLoader(dirtyState = ProjectDirtyState()).load(
            manifestPath = root.resolve("manifest.yaml"),
            into = state,
        )

        val loaded = assertIs<ProjectLoadResult.Loaded>(value = result)
        assertEquals(expected = root, actual = loaded.session.root)
        assertEquals(expected = "manifest.yaml", actual = loaded.session.manifestFileName)
        assertEquals(expected = "schemas/schema.yaml", actual = loaded.session.schemaLink)
        assertEquals(expected = listOf("rules/main.rule"), actual = loaded.session.ruleFiles)
        assertTrue(actual = loaded.session.missingFiles.isEmpty())
        assertNotNull(actual = state.parsedSchema.value)
        assertNotNull(actual = state.parsedActionSchema.value)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "alpha-rule"))
    }

    /**
     * The regression this whole feature exists for: opening a second project used to leave the first
     * one's rules and schema on screen while the base directory silently pointed at the new one.
     */
    @Test
    fun `second load fully replaces the first project`() {
        val first = createProject(name = "alpha", ruleId = "alpha-rule")
        val second = createProject(name = "beta", ruleId = "beta-rule")
        val state = newState()
        val loader = ProjectLoader(dirtyState = ProjectDirtyState())

        loader.load(manifestPath = first.resolve("manifest.yaml"), into = state)
        val result = loader.load(manifestPath = second.resolve("manifest.yaml"), into = state)

        val loaded = assertIs<ProjectLoadResult.Loaded>(value = result)
        assertEquals(expected = second, actual = loaded.session.root)
        assertEquals(expected = second.toString(), actual = state.manifestBaseDir.value)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "beta-rule"))
        assertTrue(actual = !state.ruleValue.value.text.contains(other = "alpha-rule"))
        assertTrue(actual = state.schemaText.value.contains(other = "beta"))
    }

    /** An entry without a schema must show none, not inherit the previous project's. */
    @Test
    fun `entry without schema clears the previous schema`() {
        val withSchema = createProject(name = "alpha", ruleId = "alpha-rule")
        val withoutSchema = createProject(name = "gamma", ruleId = "gamma-rule", includeSchema = false)
        val state = newState()
        val loader = ProjectLoader(dirtyState = ProjectDirtyState())

        loader.load(manifestPath = withSchema.resolve("manifest.yaml"), into = state)
        loader.load(manifestPath = withoutSchema.resolve("manifest.yaml"), into = state)

        assertEquals(expected = "", actual = state.schemaText.value)
        assertNull(actual = state.parsedSchema.value)
    }

    @Test
    fun `unparseable manifest leaves the open project untouched`() {
        val good = createProject(name = "alpha", ruleId = "alpha-rule")
        val broken = Files.createTempDirectory("broken-project")
        Files.writeString(broken.resolve("manifest.yaml"), "entries: [ this is not: valid: yaml")
        val state = newState()
        val loader = ProjectLoader(dirtyState = ProjectDirtyState())

        loader.load(manifestPath = good.resolve("manifest.yaml"), into = state)
        val result = loader.load(manifestPath = broken.resolve("manifest.yaml"), into = state)

        assertIs<ProjectLoadResult.Failed>(value = result)
        assertEquals(expected = good.toString(), actual = state.manifestBaseDir.value)
        assertTrue(actual = state.ruleValue.value.text.contains(other = "alpha-rule"))
    }

    @Test
    fun `missing rule file is reported but the project still opens`() {
        val root = createProject(name = "alpha", ruleId = "alpha-rule")
        Files.delete(root.resolve("rules/main.rule"))
        val state = newState()

        val result = ProjectLoader(dirtyState = ProjectDirtyState()).load(
            manifestPath = root.resolve("manifest.yaml"),
            into = state,
        )

        val loaded = assertIs<ProjectLoadResult.Loaded>(value = result)
        assertEquals(expected = 1, actual = loaded.session.missingFiles.size)
        assertEquals(expected = ProjectFileKind.RULE, actual = loaded.session.missingFiles.first().kind)
        assertNotNull(actual = state.parsedSchema.value)
    }

    /** A schema shared between projects lives outside the root and must not be rejected as an escape. */
    @Test
    fun `external schema link outside the project root is loaded`() {
        val parent = Files.createTempDirectory("shared-workspace")
        val shared = Files.createDirectories(parent.resolve("shared"))
        Files.writeString(shared.resolve("common.yaml"), schemaYaml(name = "shared"))

        val root = Files.createDirectories(parent.resolve("project"))
        Files.createDirectories(root.resolve("rules"))
        Files.writeString(root.resolve("rules/main.rule"), ruleText(ruleId = "shared-rule"))
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                entries:
                  - id: default
                    schema: ../shared/common.yaml
                    rules:
                      - rules/main.rule
            """.trimIndent(),
        )
        val state = newState()

        val result = ProjectLoader(dirtyState = ProjectDirtyState()).load(
            manifestPath = root.resolve("manifest.yaml"),
            into = state,
        )

        val loaded = assertIs<ProjectLoadResult.Loaded>(value = result)
        assertTrue(actual = loaded.session.missingFiles.isEmpty())
        assertEquals(expected = "../shared/common.yaml", actual = loaded.session.schemaLink)
        assertTrue(actual = state.schemaText.value.contains(other = "shared"))
    }

    @Test
    fun `manifest with several entries is flagged`() {
        val root = createProject(name = "alpha", ruleId = "alpha-rule")
        Files.writeString(
            root.resolve("manifest.yaml"),
            """
                entries:
                  - id: first
                    schema: schemas/schema.yaml
                    rules:
                      - rules/main.rule
                  - id: second
                    rules:
                      - rules/main.rule
            """.trimIndent(),
        )
        val state = newState()

        val result = ProjectLoader(dirtyState = ProjectDirtyState()).load(
            manifestPath = root.resolve("manifest.yaml"),
            into = state,
        )

        assertTrue(actual = assertIs<ProjectLoadResult.Loaded>(value = result).session.isMultiEntry)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun newState() = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))

    private fun createProject(name: String, ruleId: String, includeSchema: Boolean = true): Path {
        val root = Files.createTempDirectory("project-$name")
        Files.createDirectories(root.resolve("rules"))
        Files.createDirectories(root.resolve("schemas"))
        Files.writeString(root.resolve("rules/main.rule"), ruleText(ruleId = ruleId))
        Files.writeString(root.resolve("schemas/actions.yaml"), actionsYaml())
        if (includeSchema) {
            Files.writeString(root.resolve("schemas/schema.yaml"), schemaYaml(name = name))
        }

        Files.writeString(
            root.resolve("manifest.yaml"),
            buildString {
                appendLine("entries:")
                appendLine("  - id: default")
                if (includeSchema) appendLine("    schema: schemas/schema.yaml")
                appendLine("    actions: schemas/actions.yaml")
                appendLine("    rules:")
                appendLine("      - rules/main.rule")
            },
        )
        return root
    }

    private fun schemaYaml(name: String): String = """
        schema: $name
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
