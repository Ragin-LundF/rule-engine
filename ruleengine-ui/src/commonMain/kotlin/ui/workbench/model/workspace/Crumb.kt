package ui.workbench.model.workspace

import ui.workbench.model.InspectorItem

/**
 * One part of the context bar's trail: what it is called, and where clicking it goes.
 *
 * The target is an [InspectorItem] because the trail moves the one selection the workbench already
 * holds — a crumb is a shortcut to a selection, never a second notion of "where you are". Null means
 * the part is shown for orientation only, as the file is: nothing in the workspace selects a file.
 */
data class Crumb(
    val label: String,
    val target: InspectorItem?,
)
