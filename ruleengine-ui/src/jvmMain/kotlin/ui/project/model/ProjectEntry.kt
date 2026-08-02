package ui.project.model

/**
 * One manifest entry as the workbench holds it: an id plus the files that belong to it.
 *
 * Entries are independent — each has its own schema, actions and ordered rule files — so the editor
 * shows exactly one of them at a time and this is the unit it switches between.
 */
data class ProjectEntry(
    val id: String,
    val schemaLink: String? = null,
    val actionsLink: String? = null,
    val ruleFiles: List<String> = emptyList(),
    /** Collection this entry evaluates once per member, or null for whole-document evaluation. */
    val scope: String? = null,
)
