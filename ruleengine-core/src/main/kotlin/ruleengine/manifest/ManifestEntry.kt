package ruleengine.manifest

/**
 * One rule set of a project manifest.
 *
 * All paths are relative to the manifest file. The order of [rules] is authoritative for rule
 * execution order.
 */
data class ManifestEntry(
    val id: String,
    val schema: String? = null,
    val actions: String? = null,
    val rules: List<String> = emptyList()
)
