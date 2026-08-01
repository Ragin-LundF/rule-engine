package ui.workbench

/**
 * An action entry derived from the loaded action schema for display in the catalog.
 */
data class CatalogAction(
    val name: String,
    val argType: String,
)
