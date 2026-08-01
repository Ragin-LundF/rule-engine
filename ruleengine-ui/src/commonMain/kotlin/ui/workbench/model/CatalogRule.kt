package ui.workbench.model

/**
 * A rule entry for display in the catalog list.
 */
data class CatalogRule(
    val id: String,
    val status: CatalogRuleStatus = CatalogRuleStatus.DRAFT,
)
