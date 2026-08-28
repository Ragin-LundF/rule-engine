package ui.dock.model

import androidx.compose.runtime.Composable

/**
 * One tab of the dock.
 *
 * A plain class rather than a `data class`: it carries a composable, and lambda identity is not value
 * equality, so a generated `equals` would report two structurally identical tabs as different on every
 * recomposition and claim to be comparing them when it was comparing closures.
 *
 * [badge] belongs on the tab and not inside [content] for the reason the panel this replaces already
 * recorded: a problem count you cannot see until you expand a panel is a count that arrives after the
 * mistake has been saved.
 */
class DockTab(
    val id: String,
    val title: String,
    val badge: DockBadge? = null,
    val content: @Composable () -> Unit,
)

/** A count or a verdict shown on a tab. */
data class DockBadge(val text: String, val kind: DockBadgeKind)

enum class DockBadgeKind { OK, INFO, WARNING, ERROR }
