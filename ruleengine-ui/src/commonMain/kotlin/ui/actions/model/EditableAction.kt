package ui.actions.model
/**
 * Editable representation of a single action in the visual action-schema editor.
 */
data class EditableAction(
    val name: String = "",
    val argTypes: List<String> = emptyList(),
    val purpose: String = "",
)
