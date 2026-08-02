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

    /**
     * `toYaml` writes the manifest key by key, so a setting it does not know about is silently
     * dropped the first time the project is saved — the file loses it without the user touching it.
     */
    @Test
    fun `a scoped entry survives the round trip`() {
        val state = ManifestEditorState(
            name = "billing",
            entries = listOf(
                EditableManifestEntry(
                    id = "account-review",
                    schemaPath = "schema.yaml",
                    rulePaths = listOf("rules/a.rule"),
                    scope = "accounts",
                ),
            ),
        )

        val yaml = ManifestYamlBridge.toYaml(state = state)

        assertTrue(actual = yaml.contains(other = "scope: accounts"), message = yaml)
        assertEquals(expected = state, actual = ManifestYamlBridge.fromYaml(yaml = yaml))
    }

    @Test
    fun `an unscoped entry writes no scope key`() {
        val state = ManifestEditorState(
            name = "billing",
            entries = listOf(EditableManifestEntry(id = "plain", schemaPath = "schema.yaml")),
        )

        val yaml = ManifestYamlBridge.toYaml(state = state)

        assertTrue(actual = !yaml.contains(other = "scope:"), message = yaml)
        assertEquals(expected = "", actual = ManifestYamlBridge.fromYaml(yaml = yaml).entries.single().scope)
    }

    /**
     * Surrounding whitespace survives YAML quoting intact, so an untrimmed scope would be written as
     * `scope: " accounts "` and looked up as a field of that name — which no schema declares.
     */
    @Test
    fun `a padded scope is written trimmed`() {
        val state = ManifestEditorState(
            name = "billing",
            entries = listOf(
                EditableManifestEntry(
                    id = "account-review",
                    schemaPath = "  schema.yaml  ",
                    rulePaths = listOf("  rules/a.rule  "),
                    scope = "  accounts  ",
                ),
            ),
        )

        val yaml = ManifestYamlBridge.toYaml(state = state)

        assertTrue(actual = yaml.contains(other = "scope: accounts\n"), message = yaml)
        assertTrue(actual = yaml.contains(other = "schema: schema.yaml\n"), message = yaml)
        assertTrue(actual = yaml.contains(other = "- rules/a.rule\n"), message = yaml)
    }

    @Test
    fun `invalid YAML is marked read-only`() {
        val parsed = ManifestYamlBridge.fromYaml(yaml = "this is not: valid: [")
        assertTrue(actual = parsed.isReadOnly)
    }
}
