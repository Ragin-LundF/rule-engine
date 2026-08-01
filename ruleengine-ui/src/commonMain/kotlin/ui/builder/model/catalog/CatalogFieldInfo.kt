package ui.builder.model.catalog

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
     * `ruleengine.core.domain.dto.field.FieldDefinition.fields`, so path pickers can descend to any depth.
     */
    val nestedFields: List<CatalogFieldInfo> = emptyList(),
)
