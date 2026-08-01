package ui.editor.model

/**
 * The result of a keyboard edit: the whole new buffer, and where the caret ends up.
 *
 * Deliberately plain — no `TextFieldValue` — so the editing rules can be unit-tested without a
 * composition, which is what they lacked while they lived inline in two key handlers.
 */
data class TextEdit(
    val text: String,
    val cursor: Int,
)
