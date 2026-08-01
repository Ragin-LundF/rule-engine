package ruleengine.manifest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManifestPathResolverEscapeTest {

    private val base: Path = Path.of("/workspace/project")

    /** Rule files stay inside the project; this is the sandbox that must not be relaxed. */
    @Test
    fun `resolveWithinBase still rejects an escape`() {
        val resolution = ManifestPathResolver.resolveWithinBase(
            baseDir = base,
            relativePath = "../../etc/passwd",
            label = "rule",
        )

        val rejected = assertIs<ManifestPathResolution.Rejected>(value = resolution)
        assertTrue(actual = rejected.message.contains(other = "escapes base directory"))
    }

    /** Schema and action files may be shared between projects, so leaving the root is legitimate. */
    @Test
    fun `resolveAllowingEscape reaches a shared file above the project`() {
        val resolved = ManifestPathResolver.resolveAllowingEscape(
            baseDir = base,
            relativePath = "../shared/common.yaml",
        )

        assertEquals(expected = Path.of("/workspace/shared/common.yaml"), actual = resolved)
    }

    @Test
    fun `resolveAllowingEscape keeps paths inside the project unchanged`() {
        val resolved = ManifestPathResolver.resolveAllowingEscape(
            baseDir = base,
            relativePath = "schemas/schema.yaml",
        )

        assertEquals(expected = Path.of("/workspace/project/schemas/schema.yaml"), actual = resolved)
    }
}
