package ruleengine.manifest

import java.nio.file.Path

/**
 * Outcome of resolving a manifest-relative path against the manifest base directory.
 */
sealed interface ManifestPathResolution {
    data class Accepted(val path: Path) : ManifestPathResolution
    data class Rejected(val message: String) : ManifestPathResolution
}
