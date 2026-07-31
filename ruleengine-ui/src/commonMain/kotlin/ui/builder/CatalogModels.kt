package ui.builder

/**
 * Lightweight field info passed from the platform layer to avoid JVM-only types in commonMain.
 */
data class CatalogFieldInfo(
    val id: String,
    val type: String,
    val operators: List<String> = emptyList(),
    /** Date pattern declared for a `date` / `date_time` field, used as a value hint. Empty means ISO. */
    val format: String = "",
    /**
     * Members of a collection or object field. Recursive, mirroring
     * `ruleengine.core.domain.FieldDefinition.fields`, so path pickers can descend to any depth.
     */
    val nestedFields: List<CatalogFieldInfo> = emptyList(),
)

/**
 * Resolves the fields available at the end of a path, by walking [nestedFields] one name at a time.
 *
 * Returns an empty list when the path leaves declared territory — the caller then has nothing to
 * offer in a dropdown, which is the honest answer for an undeclared structure.
 */
fun List<CatalogFieldInfo>.fieldsAtPath(segments: List<String>): List<CatalogFieldInfo> {
    var current = this
    for (segment in segments) {
        val match = current.firstOrNull { it.id == segment } ?: return emptyList()
        current = match.nestedFields
    }
    return current
}

/** Resolves the definition a path points at, or null when the path is not fully declared. */
fun List<CatalogFieldInfo>.fieldAtPath(segments: List<String>): CatalogFieldInfo? {
    if (segments.isEmpty()) return null
    var current: CatalogFieldInfo? = null
    var candidates = this
    for (segment in segments) {
        current = candidates.firstOrNull { it.id == segment } ?: return null
        candidates = current.nestedFields
    }
    return current
}

/**
 * Lightweight action info passed from the platform layer to avoid JVM-only types in commonMain.
 */
data class CatalogActionInfo(
    val name: String,
    val argType: String,
)
