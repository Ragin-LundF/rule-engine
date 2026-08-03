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
        val resolution = ManifestPathResolver.resolveWithinBase(
            baseDir = baseDir,
            relativePath = relativePath,
            label = label,
        )
        val path = when (resolution) {
            is ManifestPathResolution.Accepted -> resolution.path
            is ManifestPathResolution.Rejected -> return ManifestFile.Unavailable(message = resolution.message)
        }

        if (!Files.isRegularFile(path)) {
            return ManifestFile.Unavailable(
                message = "$label file '$relativePath' not found (resolved to $path)",
            )
        }

        return ManifestFile.OnDisk(path = path)
    }
}
