package ui.editor.rules.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ragin_lundf.ruleengine_ui.generated.resources.Res
import io.github.ragin_lundf.ruleengine_ui.generated.resources.app
import org.jetbrains.compose.resources.painterResource
import ui.BgElevated
import ui.BgHover
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.components.StatusBadge
import ui.components.ToolbarButton
import ui.project.ProjectWorkspace
import ui.project.manifest.ManifestEntrySelection
import ui.project.model.ProjectFileKind
import ui.theme.ThemeController
import ui.theme.ThemePersistence

/**
 * Top bar: app brand, then the project actions, then the two shared-file exports.
 *
 * The six independent load/save buttons that used to live here have collapsed into one project
 * group. Loading a schema is no longer a toolbar action at all — it belongs next to the schema it
 * replaces, in the Schema area, where the user can see what is linked.
 *
 * @param inspectorOpen      Whether the right panel is open *on the Inspector tab*, which is what the
 *                           button reports as its pressed state — open on Simulate is not open here.
 * @param onToggleInspector  Opens the Inspector, or closes the panel when it is already showing it.
 * @param entrySelection     What the entry picker shows, derived by the caller from the session *and*
 *                           the parsed manifest; null hides the picker because there is no manifest at
 *                           all. Passed in rather than read off the session here — reading only the
 *                           session is what made the picker name the previous project after a sample
 *                           was loaded.
 */
@Composable
fun TopBarSection(
    workspace: ProjectWorkspace,
    onManageEntries: () -> Unit,
    inspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    entrySelection: ManifestEntrySelection?,
) {
    val isDirty = workspace.isDirty

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = BgSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        AppLogo()
        Text(
            text = "Rule Engine",
            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
        )
        StatusBadge(label = "WORKBENCH", color = PrimaryBlue)

        entrySelection?.let { selection ->
            ManifestEntryPicker(
                selection = selection,
                onSelect = { entryId -> workspace.selectEntry(entryId = entryId) },
                onAdd = {
                    // The new entry needs naming, and the Manifest area is where its card lives.
                    if (workspace.addEntry(entryId = workspace.suggestEntryId())) onManageEntries()
                },
            )
        }

        Spacer(modifier = Modifier.weight(weight = 1f))

        ProjectActions(workspace = workspace, isDirty = isDirty)

        Spacer(modifier = Modifier.width(width = 16.dp))

        SharedFileExports(workspace = workspace)

        Spacer(modifier = Modifier.width(width = 16.dp))

        // The only entry point to the Inspector that does not require finding the collapsed strip on
        // the right edge first. `primary` rather than a separate marker: the pressed look is the
        // state, so the button cannot disagree with the panel.
        ToolbarButton(
            label = "ⓘ Inspector",
            onClick = onToggleInspector,
            primary = inspectorOpen,
        )

        ToolbarButton(
            label = if (ThemeController.isDark) "☀" else "☾",
            onClick = {
                ThemeController.isDark = !ThemeController.isDark
                ThemePersistence.saveIsDark(ThemeController.isDark)
            },
        )
    }
}

@Composable
private fun ProjectActions(workspace: ProjectWorkspace, isDirty: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(label = "New Project", onClick = workspace::newProject)
        ToolbarButton(label = "Open Project…", onClick = workspace::openProject)
        ToolbarButton(
            // The dot is the only place the toolbar can say "there is work not yet on disk".
            label = if (isDirty) "Save Project •" else "Save Project",
            onClick = { workspace.saveProject() },
            primary = isDirty,
            enabled = isDirty,
        )
        ToolbarButton(label = "Save Project As…", onClick = { workspace.saveProjectAs() })
    }
}

@Composable
private fun SharedFileExports(workspace: ProjectWorkspace) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(
            label = "Save Schema As…",
            onClick = { workspace.exportShared(kind = ProjectFileKind.SCHEMA) },
        )
        ToolbarButton(
            label = "Save Actions As…",
            onClick = { workspace.exportShared(kind = ProjectFileKind.ACTIONS) },
        )
    }
}

/**
 * Which manifest entry the workbench is editing.
 *
 * In the top bar rather than in the Manifest area because the choice governs every other area: the
 * schema, the actions and the rule files on screen all belong to the entry named here.
 *
 * Read-only when the manifest has no project behind it — a loaded sample. It still names the entry,
 * because that is the question the top bar is here to answer, but it drops the `▾` and does not open:
 * both operations behind the menu need a session, so a menu would offer two controls that do nothing.
 */
@Composable
private fun ManifestEntryPicker(
    selection: ManifestEntrySelection,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }
    val activeEntryId = selection.activeEntryId

    if (!selection.editable) {
        ToolbarButton(label = "Entry: $activeEntryId", onClick = {}, enabled = false)
        return
    }

    Box {
        ToolbarButton(label = "Entry: $activeEntryId ▾", onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(color = BgElevated)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
        ) {
            selection.entryIds.forEach { entryId ->
                val isSelected = entryId == activeEntryId
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(entryId)
                    },
                    modifier = Modifier.background(
                        color = if (isSelected) BgHover else BgElevated,
                        shape = RoundedCornerShape(size = 6.dp),
                    ),
                ) {
                    Text(
                        text = entryId,
                        style = MaterialTheme.typography.body2,
                        color = if (isSelected) PrimaryBlue else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            Divider(color = BorderColor, thickness = 1.dp)
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    onAdd()
                },
            ) {
                Text(
                    text = "+ New entry…",
                    style = MaterialTheme.typography.body2,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.app),
        contentDescription = "Rule Engine Workbench",
        modifier = modifier
            .clip(shape = CircleShape)
            .background(color = PrimaryBlue.copy(alpha = 0.12f))
            .padding(all = 4.dp)
            .size(size = 30.dp),
    )
}
