package ui.components.header.model

/**
 * One entry in a binding chip's menu.
 *
 * Flat rather than a sealed hierarchy of item / separator / heading: a menu of five entries does not
 * earn three types, and [sectionTitle] and [separatorBefore] describe what precedes an item, so the
 * order of the list is the order on screen with nothing to reconcile.
 */
data class BindingMenuItem(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val separatorBefore: Boolean = false,
    val sectionTitle: String? = null,
)
