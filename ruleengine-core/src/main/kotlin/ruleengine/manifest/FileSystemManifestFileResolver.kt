package ruleengine.manifest

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves a manifest's files against [baseDir] on the local filesystem.
 *
 * Reports a missing file itself rather than leaving it to the loader that would read it, so the
 * builder can name both the path the manifest spells and the path it resolved to.
 */
class FileSystemManifestFileResolver(private val baseDir: Path) : ManifestFileResolver {
    override fun resolve(relativePath: String, label: String): ManifestFile {
        // `../` is allowed on purpose: a manifest is trusted and may reference shared files.
        val path = ManifestPathResolver.resolveAllowingEscape(baseDir = baseDir, relativePath = relativePath)

        if (!Files.isRegularFile(path)) {
            return ManifestFile.Unavailable(
                message = "$label file '$relativePath' not found (resolved to $path)",
            )
        }

        return ManifestFile.OnDisk(path = path)
    }
}
