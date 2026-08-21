package ui.builder.model.catalog

/**
 * Lightweight action info passed from the platform layer to avoid JVM-only types in commonMain.
 */
data class CatalogActionInfo(
    val name: String,
    val argType: String,
    /**
     * How many rules emit this action, counted over the rules currently loaded.
     *
     * Read by the inspector only. `builderCatalogActionsFrom` leaves it at zero, because the builder
     * is filling in one argument rather than describing the action.
     */
    val usages: Int = 0,
)
