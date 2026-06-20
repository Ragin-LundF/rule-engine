package ui.builder

/**
 * Lightweight field info passed from the platform layer to avoid JVM-only types in commonMain.
 */
data class CatalogFieldInfo(
    val id: String,
    val type: String,
    val operators: List<String> = emptyList(),
)

/**
 * Lightweight action info passed from the platform layer to avoid JVM-only types in commonMain.
 */
data class CatalogActionInfo(
    val name: String,
    val argType: String,
)
