package ruleengine.manifest

data class ManifestEntry(
    val id: String,
    val schema: String? = null,
    val actions: String? = null,
    val rules: List<String> = emptyList(),
)

data class ProjectManifest(
    val name: String? = null,
    val entries: List<ManifestEntry> = emptyList(),
)

