package ui.builder.model.catalog

/**
 * Lightweight action info passed from the platform layer to avoid JVM-only types in commonMain.
 */
data class CatalogActionInfo(
    val name: String,
    val argType: String,
)
