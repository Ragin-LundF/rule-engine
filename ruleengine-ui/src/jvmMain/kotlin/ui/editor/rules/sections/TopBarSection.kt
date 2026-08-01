package ui.editor.rules.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ragin_lundf.ruleengine_ui.generated.resources.Res
import io.github.ragin_lundf.ruleengine_ui.generated.resources.app
import org.jetbrains.compose.resources.painterResource
import ui.BgSurface
import ui.PrimaryBlue
import ui.TextPrimary
import ui.components.StatusBadge
import ui.components.ToolbarButton
import ui.project.ProjectFileKind
import ui.project.ProjectWorkspace
import ui.theme.ThemeController
import ui.theme.ThemePersistence

/**
 * Top bar: app brand, then the project actions, then the two shared-file exports.
 *
 * The six independent load/save buttons that used to live here have collapsed into one project
 * group. Loading a schema is no longer a toolbar action at all — it belongs next to the schema it
 * replaces, in the Schema area, where the user can see what is linked.
 */
@Composable
fun TopBarSection(workspace: ProjectWorkspace) {
    val session by workspace.session
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
        Spacer(modifier = Modifier.weight(weight = 1f))

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
                // A multi-entry manifest can only be written as a single-entry copy, so plain Save
                // is refused rather than silently dropping the other entries.
                enabled = isDirty && session?.isMultiEntry != true,
            )
            ToolbarButton(label = "Save Project As…", onClick = { workspace.saveProjectAs() })
        }

        Spacer(modifier = Modifier.width(width = 16.dp))

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

        Spacer(modifier = Modifier.width(width = 16.dp))

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
