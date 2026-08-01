package ui.workbench

/**
 * Validation status of a rule as shown in the catalog and rule tree.
 */
enum class CatalogRuleStatus(val label: String) {
    VALID("valid"),
    INVALID("invalid"),
    DRAFT("draft"),
}
