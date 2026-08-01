package ui.manifest.model
/**
 * Local editing state for the Manifest builder.
 */
data class ManifestEditorState(
    val name: String = "",
    val entries: List<EditableManifestEntry> = emptyList(),
    val isReadOnly: Boolean = false,
)
