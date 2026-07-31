package ruleengine.manifest

/**
 * Deserialised project manifest: a named collection of [ManifestEntry] rule sets.
 */
data class ProjectManifest(
    val name: String? = null,
    val entries: List<ManifestEntry> = emptyList()
)
