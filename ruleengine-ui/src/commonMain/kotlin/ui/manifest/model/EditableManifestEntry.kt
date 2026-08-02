package ui.manifest.model
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
    /**
     * Collection to evaluate once per member, or empty for whole-document evaluation.
     *
     * Carried even though the editor barely shows it: [ui.manifest.ManifestYamlBridge.toYaml] writes
     * the manifest key by key, so a field missing here is silently dropped from the file the next
     * time the project is saved.
     */
    val scope: String = "",
)
