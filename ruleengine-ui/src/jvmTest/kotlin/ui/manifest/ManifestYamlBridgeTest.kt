package ui.manifest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestYamlBridgeTest {

    @Test
    fun `round-trip preserves single manifest entry`() {
        val state = ManifestEditorState(
            name = "transaction-v1",
            entries = listOf(
                EditableManifestEntry(
                    id = "default",
                    schemaPath = "schema.yaml",
                    actionsPath = "actions.yaml",
                    rulePaths = listOf("rules/rent.rule", "rules/vip.rule"),
                ),
            ),
        )
        val yaml = ManifestYamlBridge.toYaml(state = state)
        val parsed = ManifestYamlBridge.fromYaml(yaml = yaml)
        assertEquals(expected = state, actual = parsed)
    }

    @Test
    fun `empty paths are filtered from YAML but do not break parsing`() {
        val state = ManifestEditorState(
            name = "",
            entries = listOf(
                EditableManifestEntry(
                    id = "default",
                    schemaPath = "",
                    actionsPath = "actions.yaml",
                    rulePaths = listOf("", "rules/rent.rule", ""),
                ),
            ),
        )
        val yaml = ManifestYamlBridge.toYaml(state = state)
        val parsed = ManifestYamlBridge.fromYaml(yaml = yaml)
        assertTrue(actual = parsed.entries.first().schemaPath.isEmpty())
        assertEquals(
            expected = listOf("rules/rent.rule"),
            actual = parsed.entries.first().rulePaths,
        )
    }

    @Test
    fun `invalid YAML is marked read-only`() {
        val parsed = ManifestYamlBridge.fromYaml(yaml = "this is not: valid: [")
        assertTrue(actual = parsed.isReadOnly)
    }
}
