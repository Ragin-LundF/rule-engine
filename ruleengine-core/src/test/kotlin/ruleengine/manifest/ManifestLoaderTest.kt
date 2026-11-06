package ruleengine.manifest

import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class ManifestLoaderTest {
    @Test
    fun `loads manifest and checks referenced files exist`() {
        val path = Path.of("src/test/resources/manifest.yaml")
        val manifest = ManifestLoader.load(path)

        assertEquals(expected = "sample-project", actual = manifest.name)
        assertEquals(expected = 1, actual = manifest.entries.size)

        val entry = manifest.entries[0]
        assertEquals(expected = "sample", actual = entry.id)

        val base = path.parent

        entry.schema?.let { schemaPath ->
            val p = base.resolve(schemaPath)
            assertTrue(actual = Files.exists(p), message = "schema file $p should exist")
        } ?: fail("schema missing in manifest entry")

        entry.actions?.let { actionsPath ->
            val p = base.resolve(actionsPath)
            assertTrue(actual = Files.exists(p), message = "actions file $p should exist")
        } ?: fail("actions missing in manifest entry")

        assertTrue(actual = entry.rules.isNotEmpty(), message = "manifest should reference at least one rule file")
        entry.rules.forEach { r ->
            val p = base.resolve(r)
            assertTrue(actual = Files.exists(p), message = "rule file $p should exist")
        }
    }

    /** No `scope` means whole-document evaluation, which is what every existing manifest asks for. */
    @Test
    fun `an entry without a scope carries none`() {
        val manifest = ManifestLoader.load(Path.of("src/test/resources/manifest.yaml"))

        assertEquals(expected = null, actual = manifest.entries.single().scope)
    }

    @Test
    fun `a declared scope is read from the entry`() {
        val manifest = ManifestLoader.load(Path.of("src/test/resources/scoped-accounts/manifest.yaml"))

        assertEquals(expected = "accounts", actual = manifest.entries.single().scope)
    }

    private fun entryYaml(extra: String): String {
        return "entries:\n  - id: e\n    schema: s.yaml\n    rules: [r.rule]\n    $extra\n"
    }

    @Test
    fun `missingCollections empty is read from the entry and absent means strict`() {
        val lenient = ManifestLoader.loadFromString(content = entryYaml(extra = "missingCollections: empty"))
        val strict = ManifestLoader.loadFromString(content = entryYaml(extra = "scope: null"))

        assertEquals(expected = "empty", actual = lenient.entries.single().missingCollections)
        assertEquals(expected = null, actual = strict.entries.single().missingCollections)
    }

    @Test
    fun `another missingCollections value fails to load`() {
        val other = assertFailsWith<IllegalArgumentException> {
            ManifestLoader.loadFromString(content = entryYaml(extra = "missingCollections: other"))
        }

        assertTrue(actual = other.message.orEmpty().contains(other = "the only value is 'empty'"))
    }

    @Test
    fun `a misspelt entry key fails naming the entry, the key and the closest known key`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ManifestLoader.loadFromString(content = entryYaml(extra = "missingCollection: empty"))
        }

        assertTrue(actual = error.message.orEmpty().contains(other = "entry 'e'"), message = error.message)
        assertTrue(actual = error.message.orEmpty().contains(other = "'missingCollection'"), message = error.message)
        assertTrue(actual = error.message.orEmpty().contains(other = "did you mean 'missingCollections'"))
    }

    @Test
    fun `an unknown top-level key fails, in YAML and in JSON`() {
        val yaml = assertFailsWith<IllegalArgumentException> {
            ManifestLoader.loadFromString(content = "nme: p\n" + entryYaml(extra = "scope: accounts"))
        }
        val json = assertFailsWith<IllegalArgumentException> {
            ManifestLoader.loadFromString(content = """{"name": "p", "version": 2, "entries": []}""")
        }

        assertTrue(actual = yaml.message.orEmpty().contains(other = "unknown key 'nme'"), message = yaml.message)
        assertTrue(actual = json.message.orEmpty().contains(other = "unknown key 'version'"), message = json.message)
    }

    @Test
    fun `every known key loads`() {
        val manifest = ManifestLoader.loadFromString(
            content = """
                name: p
                entries:
                  - id: e
                    schema: s.yaml
                    actions: a.yaml
                    rules: [r.rule]
                    scope: accounts
                    missingCollections: empty
            """.trimIndent()
        )

        assertEquals(expected = "accounts", actual = manifest.entries.single().scope)
    }

    /**
     * Every property of the model is a known key, so adding one without listing it fails here. Read
     * through Java reflection, which sees a new property whatever its default.
     */
    @Test
    fun `the known key lists cover every model property`() {
        assertEquals(
            expected = ManifestLoader.ENTRY_KEYS.toSet(),
            actual = propertiesOf(type = ManifestEntry::class.java),
        )
        assertEquals(
            expected = ManifestLoader.TOP_LEVEL_KEYS.toSet(),
            actual = propertiesOf(type = ProjectManifest::class.java),
        )
    }

    private fun propertiesOf(type: Class<*>): Set<String> {
        return type.declaredFields.filterNot { field -> Modifier.isStatic(field.modifiers) }.map { it.name }.toSet()
    }
}
