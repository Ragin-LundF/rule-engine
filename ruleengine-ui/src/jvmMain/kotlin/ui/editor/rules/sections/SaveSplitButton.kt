package ui.editor.rules.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.ToolbarButton
import ui.components.header.HeaderMenu
import ui.components.header.HeaderMenuDivider
import ui.components.header.HeaderMenuItem
import ui.components.header.HeaderMenuSection
import ui.project.ProjectWorkspace
import ui.project.model.ProjectFileKind

/**
 * Saving: the thing itself on the button, its variants behind the caret.
 *
 * The one accent-coloured control in the bar. *Save Project As…* and the two shared-file exports are
 * behind the caret because they are rare, and because a bar that gives them equal billing is a bar
 * nobody can read at a glance.
 *
 * No glyph and no dot on the label: the download-arrow glyphs available at this text size (`⤓`, `↧`,
 * `⇩`) are hairlines, and the `UNSAVED` badge beside the entry already says what the dot said, once,
 * in a word.
 */
@Composable
fun SaveSplitButton(workspace: ProjectWorkspace, isDirty: Boolean) {
    var expanded by remember { mutableStateOf(value = false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            label = "Save",
            onClick = { workspace.saveProject() },
            primary = isDirty,
            enabled = isDirty,
        )
        Box {
            // Narrower than a normal toolbar button: it is the other half of the button beside it, not
            // a control of its own.
            ToolbarButton(
                label = "▼",
                onClick = { expanded = true },
                modifier = Modifier.width(width = 44.dp),
            )
            HeaderMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                HeaderMenuSection(title = "Save")
                HeaderMenuItem(
                    label = "Save Project",
                    enabled = isDirty,
                    onClick = {
                        expanded = false
                        workspace.saveProject()
                    },
                )
                HeaderMenuItem(
                    label = "Save Project As…",
                    onClick = {
                        expanded = false
                        workspace.saveProjectAs()
                    },
                )
                HeaderMenuDivider()
                // The files another project can read too. They are the *project's* copies here; each
                // area also exports its own from its header, next to the file it is about.
                HeaderMenuSection(title = "Shared files")
                HeaderMenuItem(
                    label = "Save Schema As…",
                    onClick = {
                        expanded = false
                        workspace.exportShared(kind = ProjectFileKind.SCHEMA)
                    },
                )
                HeaderMenuItem(
                    label = "Save Actions As…",
                    onClick = {
                        expanded = false
                        workspace.exportShared(kind = ProjectFileKind.ACTIONS)
                    },
                )
            }
        }
    }
}

