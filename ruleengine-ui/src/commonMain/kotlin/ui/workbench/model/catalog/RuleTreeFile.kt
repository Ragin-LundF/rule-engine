package ui.workbench.model.catalog

/**
 * One rule file inside a manifest entry, with its rules already mapped to catalog rows so
 * [RuleTreePanel] can render status dots without depending on the parser AST directly.
 */
data class RuleTreeFile(val relativePath: String, val rules: List<CatalogRule>)
