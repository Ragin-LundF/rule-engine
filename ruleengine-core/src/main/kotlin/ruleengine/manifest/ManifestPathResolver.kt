package ruleengine.manifest

import java.nio.file.Path

/**
 * Resolves paths referenced by a manifest entry against the manifest's own directory.
 *
 * A manifest may reference any file the process can read: `../shared/schema.yaml` is how projects
 * share a schema, and the same holds for rule and action files. Manifests are therefore trusted input
 * and must come from a trusted source. The engine and the editor resolve with [resolveAllowingEscape].
 */
object ManifestPathResolver {
    /**
     * Resolves [relativePath] and accepts it only when it stays inside [baseDir].
     *
     * Not a loading rule any more — loading uses [resolveAllowingEscape]. It answers "does the
     * project own this file", which the editor needs before deleting files of a removed entry: a
     * shared file above the project is read by other projects and must be left alone.
     */
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
     * Resolves [relativePath] against [baseDir], normalising `.` and `..`. A path may leave [baseDir]
     * (`../shared/schema.yaml`), and an absolute path is taken as it is.
     */
    fun resolveAllowingEscape(baseDir: Path, relativePath: String): Path {
        return baseDir.toAbsolutePath().normalize().resolve(relativePath).normalize()
    }
}
