package ruleengine.manifest

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
}
