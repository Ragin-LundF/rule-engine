package ui.workbench.model

/**
 * A field entry derived from the loaded schema for display in the catalog.
 */
data class CatalogField(
    val id: String,
    val type: String,
    val operators: List<String> = emptyList(),
    val normalizers: List<String> = emptyList(),
    val alias: String? = null,
)
