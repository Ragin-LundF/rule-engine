package ruleengine.manifest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestPathResolverTest {
    @Test
    fun `resolves relative manifest path within base dir`() {
        val baseDir = Path.of("/tmp/rule-engine-ui-manifest").toAbsolutePath().normalize()
        val resolution = ManifestPathResolver.resolveWithinBase(
            baseDir = baseDir,
            relativePath = "rules/nested.rule",
            label = "rule"
        )

        val accepted = resolution as ManifestPathResolution.Accepted
        assertEquals(expected = baseDir.resolve("rules/nested.rule").normalize(), actual = accepted.path)
    }

    @Test
    fun `rejects path escaping base dir`() {
        val baseDir = Path.of("/tmp/rule-engine-ui-manifest").toAbsolutePath().normalize()
        val resolution = ManifestPathResolver.resolveWithinBase(
            baseDir = baseDir,
            relativePath = "../escape.rule",
            label = "rule"
        )

        val rejected = resolution as ManifestPathResolution.Rejected
        assertTrue(actual = rejected.message.contains(other = "escapes base directory"))
    }
}

