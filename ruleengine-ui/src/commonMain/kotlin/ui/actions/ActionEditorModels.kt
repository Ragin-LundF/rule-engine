package ui.actions

import ruleengine.core.domain.dto.ActionArgType

/**
 * Editable representation of a single action in the visual action-schema editor.
 */
data class EditableAction(
    val name: String = "",
    val argTypes: List<String> = emptyList(),
    val purpose: String = "",
)

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

/** All known action argument type ids exposed in the editor, lowercase, in declaration order. */
val KnownActionArgTypes: List<String> =
    ActionArgType.entries.map { argType -> argType.name.lowercase() }
