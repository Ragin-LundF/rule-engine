package ui.actions
/**
 * Immutable snapshot of the visual action-schema editor state.
 *
 * @param actions Ordered list of editable action rows.
 * @param isReadOnly True when the loaded YAML cannot be round-tripped visually.
 */
data class ActionEditorState(
    val actions: List<EditableAction> = emptyList(),
    val isReadOnly: Boolean = false,
) {
    companion object {
        val Empty = ActionEditorState()
    }
}
