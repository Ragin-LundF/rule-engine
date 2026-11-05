package ui.project.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class AtomicFilesTest {

    @Test
    fun `replaces an existing file and leaves no temporary sibling`() {
        val dir = Files.createTempDirectory("atomic-replace")
        val target = dir.resolve("pricing.rule")
        Files.writeString(target, "old")

        AtomicFiles.writeString(path = target, content = "new")

        assertEquals(expected = "new", actual = Files.readString(target))
        assertEquals(expected = listOf(target), actual = dir.listDirectoryEntries())
    }

    @Test
    fun `writes through a symlink and keeps the link`() {
        val dir = Files.createTempDirectory("atomic-symlink")
        val real = dir.resolve("real.rule")
        Files.writeString(real, "old")
        val link = Files.createSymbolicLink(dir.resolve("pricing.rule"), real)

        AtomicFiles.writeString(path = link, content = "new")

        assertTrue(actual = Files.isSymbolicLink(link))
        assertEquals(expected = "new", actual = Files.readString(real))
    }

    @Test
    fun `writes through a chain of links to a file that does not exist yet`() {
        val dir = Files.createTempDirectory("atomic-dangling")
        val missing = dir.resolve("missing.rule")
        val middle = Files.createSymbolicLink(dir.resolve("middle.rule"), missing.fileName)
        val link = Files.createSymbolicLink(dir.resolve("pricing.rule"), middle.fileName)

        AtomicFiles.writeString(path = link, content = "new")

        assertTrue(actual = Files.isSymbolicLink(link))
        assertTrue(actual = Files.isSymbolicLink(middle))
        assertEquals(expected = "new", actual = Files.readString(missing))
    }

    @Test
    fun `a failed replace keeps the old target and cleans up the temporary sibling`() {
        val dir = Files.createTempDirectory("atomic-failure")
        // A non-empty directory cannot be replaced by a file, so the final move fails.
        val target = Files.createDirectory(dir.resolve("occupied"))
        Files.writeString(target.resolve("keep.txt"), "kept")

        assertFails { AtomicFiles.writeString(path = target, content = "new") }

        assertEquals(expected = "kept", actual = Files.readString(target.resolve("keep.txt")))
        assertEquals(expected = listOf<Path>(target), actual = dir.listDirectoryEntries())
    }
}
