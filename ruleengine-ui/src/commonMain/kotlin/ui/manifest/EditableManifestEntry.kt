package ui.manifest
/**
 * Editable representation of a single project manifest entry.
 *
 * The UI keeps one primary entry for the common schema/actions/rules layout. The
 * core model supports multiple entries for multi-rule-set projects.
 */
data class EditableManifestEntry(
    val id: String = "default",
    val schemaPath: String = "",
    val actionsPath: String = "",
    val rulePaths: List<String> = emptyList(),
)
