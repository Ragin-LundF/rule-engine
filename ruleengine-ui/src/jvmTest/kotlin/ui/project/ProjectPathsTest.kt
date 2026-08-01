package ui.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectPathsTest {

    @Test
    fun `a file inside the project gets a plain relative path`() {
        val root = Files.createTempDirectory("project")
        val target = root.resolve("schemas").resolve("schema.yaml")

        assertEquals(expected = "schemas/schema.yaml", actual = ProjectPaths.relativize(root = root, target = target))
    }

    /** A shared schema lives outside the project, and staying relative is what keeps it portable. */
    @Test
    fun `a file outside the project is reached with dot dot`() {
        val parent = Files.createTempDirectory("workspace")
        val root = Files.createDirectories(parent.resolve("project"))
        val target = parent.resolve("shared").resolve("common.yaml")

        assertEquals(
            expected = "../shared/common.yaml",
            actual = ProjectPaths.relativize(root = root, target = target),
        )
    }

    @Test
    fun `external paths are recognised`() {
        assertTrue(actual = ProjectPaths.isExternal(relativePath = "../shared/common.yaml"))
        assertTrue(actual = ProjectPaths.isExternal(relativePath = "/absolute/common.yaml"))
        assertFalse(actual = ProjectPaths.isExternal(relativePath = "schemas/schema.yaml"))
    }
}
