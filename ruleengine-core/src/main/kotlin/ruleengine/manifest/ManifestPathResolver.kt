package ruleengine.manifest

import java.nio.file.Path

/**
 * Resolves paths referenced by a manifest entry against the manifest's own directory and rejects
 * every candidate that escapes it (for example `../../etc/passwd`).
 */
object ManifestPathResolver {
    fun resolveWithinBase(baseDir: Path, relativePath: String, label: String): ManifestPathResolution {
        val normalizedBaseDir = baseDir.toAbsolutePath().normalize()
        val candidatePath = normalizedBaseDir.resolve(relativePath).normalize()

        return if (candidatePath.startsWith(normalizedBaseDir)) {
            ManifestPathResolution.Accepted(path = candidatePath)
        } else {
            ManifestPathResolution.Rejected(
                message = "Manifest $label path '$relativePath' escapes base directory '$normalizedBaseDir'"
            )
        }
    }
}
