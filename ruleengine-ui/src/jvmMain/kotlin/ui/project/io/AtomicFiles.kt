package ui.project.io

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Replaces a file in one step, so a crash or a full disk part-way through a write leaves the old file
 * intact instead of a truncated one.
 *
 * The bytes go to a hidden sibling first, which keeps the final move on the same filesystem.
 */
object AtomicFiles {

    /** The usual operating-system limit; past it the links form a loop. */
    private const val MAX_LINK_HOPS = 40

    fun write(path: Path, bytes: ByteArray) {
        val target = linkTarget(path = path)
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        try {
            Files.write(temporary, bytes)
            moveOver(source = temporary, target = target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun writeString(path: Path, content: String) {
        write(path = path, bytes = content.toByteArray(charset = Charsets.UTF_8))
    }

    /**
     * A symlink is written through, as a plain write does; moving over it would replace the link itself.
     *
     * The chain is followed hop by hop rather than with `toRealPath`, so a dangling link still leads to
     * the file it names — the write then creates it.
     */
    private fun linkTarget(path: Path): Path {
        var current = path
        repeat(times = MAX_LINK_HOPS) {
            if (!Files.isSymbolicLink(current)) return current
            current = current.resolveSibling(Files.readSymbolicLink(current))
        }
        throw FileSystemException(path.toString(), null, "Too many levels of symbolic links")
    }

    private fun moveOver(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
