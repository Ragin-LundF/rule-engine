package ui.components.header.model

/**
 * What an area is bound to: the file it edits, and how to change it.
 *
 * One control in one position for what used to be three different things — the Rules area's unlabelled
 * `☰` file menu, the Schema and Actions areas' full-width linked-file bar, and the Manifest area's
 * nothing at all.
 */
data class BindingSpec(
    /** The kind of binding, shown as a quiet key before the value — "FILE", "ENTRY". */
    val label: String,
    /** The binding itself: a file name, an entry id. Truncated before the bar is. */
    val value: String,
    val badge: BindingBadge? = null,
    val items: List<BindingMenuItem> = emptyList(),
)
