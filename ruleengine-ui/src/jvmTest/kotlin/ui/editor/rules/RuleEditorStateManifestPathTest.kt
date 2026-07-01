package ui.editor.rules

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ruleengine.manifest.ManifestEntry

class RuleEditorStateManifestPathTest {
    @Test
    fun `load manifest entry loads files inside base dir`() {
        val baseDir = createManifestBaseDir()
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        state.manifestBaseDir.value = baseDir.toString()

        state.loadManifestEntry(
            entry = ManifestEntry(
                id = "sample",
                schema = "schema.yaml",
                actions = "actions.yaml",
                rules = listOf("rules/nested.rule")
            )
        )

        assertEquals(expected = StatusKind.SUCCESS, actual = state.statusKind.value)
        assertTrue(actual = state.status.value.contains(other = "Loaded 'sample'"))
        assertTrue(actual = state.status.value.contains(other = "schema"))
        assertTrue(actual = state.status.value.contains(other = "actions"))
        assertTrue(actual = state.status.value.contains(other = "1 rule file(s)"))
        assertEquals(expected = "rules/nested.rule", actual = state.selectedManifestRuleFile.value)
        assertTrue(actual = state.ruleValue.value.text.isNotBlank())
        assertFalse(actual = state.diagnosticsText.value.contains(other = "escapes base directory"))
    }

    @Test
    fun `load manifest entry rejects escaped paths`() {
        val baseDir = createManifestBaseDir()
        val state = RuleEditorState(scope = CoroutineScope(context = EmptyCoroutineContext))
        state.manifestBaseDir.value = baseDir.toString()

        state.loadManifestEntry(
            entry = ManifestEntry(
                id = "sample",
                schema = "schema.yaml",
                actions = "actions.yaml",
                rules = listOf("../escape.rule")
            )
        )

        assertEquals(expected = StatusKind.ERROR, actual = state.statusKind.value)
        assertTrue(actual = state.status.value.contains(other = "escapes base directory"))
        assertTrue(actual = state.diagnosticsText.value.contains(other = "escapes base directory"))
        assertEquals(expected = "", actual = state.ruleValue.value.text)
        assertEquals(expected = null, actual = state.parsedSchema.value)
        assertEquals(expected = null, actual = state.parsedActionSchema.value)
    }

    private fun createManifestBaseDir(): Path {
        val baseDir = Files.createTempDirectory("rule-editor-manifest")
        Files.writeString(baseDir.resolve("schema.yaml"), schemaYaml())
        Files.writeString(baseDir.resolve("actions.yaml"), actionsYaml())
        Files.createDirectories(baseDir.resolve("rules"))
        Files.writeString(baseDir.resolve("rules/nested.rule"), ruleText())
        return baseDir
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

    private fun ruleText(): String {
        return """
            rule "nested-rent" {
              when purpose equals "rent"
              then label "rent"
            }
        """.trimIndent()
    }
}

