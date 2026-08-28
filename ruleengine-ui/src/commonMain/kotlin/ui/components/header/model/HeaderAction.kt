package ui.components.header.model

/**
 * One action offered by an area's header.
 *
 * Data and an [id], never a lambda: the header reports the id and the area decides what it means. A
 * callback in here would make the model uncomparable in tests and would invite areas to put behaviour
 * in a list that is meant to describe a bar.
 *
 * [icon] is what a [ActionEmphasis.STANDARD] action shrinks to. Without one it keeps its label, because
 * an unlabelled button with no glyph is a blank square.
 */
data class HeaderAction(
    val id: String,
    val label: String,
    val icon: String? = null,
    val emphasis: ActionEmphasis = ActionEmphasis.STANDARD,
    val enabled: Boolean = true,
)
