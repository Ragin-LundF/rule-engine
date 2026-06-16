package ui.editor.rules

import java.nio.file.Path

sealed interface ManifestPathResolution {
    data class Accepted(val path: Path) : ManifestPathResolution
    data class Rejected(val message: String) : ManifestPathResolution
}

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

