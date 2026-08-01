package ui.manifest

import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `round-trip preserves several entries and their order`() {
        val state = ManifestEditorState(
            name = "two-sets",
            entries = listOf(
                EditableManifestEntry(
                    id = "transactions",
                    schemaPath = "schemas/tx.yaml",
                    rulePaths = listOf("rules/a.rule"),
                ),
                EditableManifestEntry(id = "risk", actionsPath = "schemas/risk-actions.yaml"),
            ),
        )

        val parsed = ManifestYamlBridge.fromYaml(yaml = ManifestYamlBridge.toYaml(state = state))

        assertEquals(expected = state, actual = parsed)
    }

    /** A freshly added entry has no files yet; dropping it would delete it before it was ever used. */
    @Test
    fun `an entry with only an id survives the round trip`() {
        val state = ManifestEditorState(name = "x", entries = listOf(EditableManifestEntry(id = "brand-new")))

        val parsed = ManifestYamlBridge.fromYaml(yaml = ManifestYamlBridge.toYaml(state = state))

        assertEquals(expected = listOf("brand-new"), actual = parsed.entries.map { it.id })
    }

    @Test
    fun `invalid YAML is marked read-only`() {
        val parsed = ManifestYamlBridge.fromYaml(yaml = "this is not: valid: [")
        assertTrue(actual = parsed.isReadOnly)
    }
}
