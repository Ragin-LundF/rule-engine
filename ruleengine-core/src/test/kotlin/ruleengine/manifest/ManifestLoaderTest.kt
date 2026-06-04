package ruleengine.manifest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import java.nio.file.Path

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
            assertTrue(java.nio.file.Files.exists(p), "schema file $p should exist")
        } ?: fail("schema missing in manifest entry")

        entry.actions?.let { actionsPath ->
            val p = base.resolve(actionsPath)
            assertTrue(java.nio.file.Files.exists(p), "actions file $p should exist")
        } ?: fail("actions missing in manifest entry")

        assertTrue(entry.rules.isNotEmpty(), "manifest should reference at least one rule file")
        entry.rules.forEach { r ->
            val p = base.resolve(r)
            assertTrue(java.nio.file.Files.exists(p), "rule file $p should exist")
        }
    }
}

