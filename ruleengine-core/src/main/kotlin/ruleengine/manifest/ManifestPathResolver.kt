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

    /**
     * Resolves a path that is allowed to leave [baseDir].
     *
     * Schema and action files may be shared between projects, so a manifest referring to
     * `../shared/schemas/common.yaml` is legitimate and must not be rejected. Rule files stay
     * confined to the project and keep using [resolveWithinBase] — the escape is a deliberate
     * exception for the two file kinds a user can link, not a general relaxation.
     */
    fun resolveAllowingEscape(baseDir: Path, relativePath: String): Path {
        return baseDir.toAbsolutePath().normalize().resolve(relativePath).normalize()
    }
}
