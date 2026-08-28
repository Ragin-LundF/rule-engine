package ui.editor.rules.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ui.components.ToolbarButton
import ui.components.header.HeaderMenu
import ui.components.header.HeaderMenuItem
import ui.components.header.HeaderMenuSection
import ui.project.ProjectWorkspace

/**
 * The project's own actions, behind one control.
 *
 * They were two full-width buttons on the bar — *New Project* and *Open Project…* — beside two more
 * for saving and two more for exporting. Eight text buttons in a fixed row is what stopped the bar
 * fitting a 1300 px window; it is also a flat list that gave "New Project" the same weight as saving,
 * which is the thing actually done a hundred times a day.
 */
@Composable
fun ProjectMenuButton(workspace: ProjectWorkspace) {
    var expanded by remember { mutableStateOf(value = false) }

    Box {
        // A word and a caret, no glyph. Every symbol tried for "project" — ▤, ☰, ⌂ — was either a
        // hairline at this size or meant something else already; "Project" is seven letters and means
        // exactly one thing. The caret is `▼` and not the smaller `▾`: at 12 sp the small triangle is
        // a smudge, and a menu that does not look like a menu is not one.
        ToolbarButton(label = "Project ▼", onClick = { expanded = true })
        HeaderMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HeaderMenuSection(title = "Project")
            HeaderMenuItem(
                label = "New Project",
                onClick = {
                    expanded = false
                    workspace.newProject()
                },
            )
            HeaderMenuItem(
                label = "Open Project…",
                onClick = {
                    expanded = false
                    workspace.openProject()
                },
            )
        }
    }
}
