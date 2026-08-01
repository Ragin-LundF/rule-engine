package ui.schema.model
/**
 * Immutable snapshot of the visual schema editor state.
 *
 * @param schemaName  Name of the schema (maps to the `schema:` YAML key).
 * @param fields      Ordered list of editable field rows.
 * @param isReadOnly  True when the loaded YAML contains unsupported constructs
 *                    (e.g. custom normalizer groups); editing is disabled.
 */
data class SchemaEditorState(
    val schemaName: String = "",
    val fields: List<EditableField> = emptyList(),
    val isReadOnly: Boolean = false,
) {
    companion object {
        val Empty = SchemaEditorState()
    }
}
