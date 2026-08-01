package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgElevated
import ui.BorderColor
import ui.TextMuted
import ui.TextSecondary
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.project.ProjectPaths
import ui.project.ProjectWorkspace

/**
 * Status bar: the current message with a colour-coded dot, plus where the project lives.
 *
 * The project line matters more than it sounds: with several projects open over a session, "which
 * one am I editing and is it saved" is otherwise only answerable by opening a file dialog.
 */
@Suppress("FunctionNaming")
@Composable
fun StatusBarSection(state: RuleEditorState, workspace: ProjectWorkspace) {
    val statusKind by state.statusKind
    val status by state.status
    val parsedSchema by state.parsedSchema
    val session by workspace.session
    val isDirty = workspace.isDirty

    Spacer(modifier = Modifier.height(height = 8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 10.dp))
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        val dot = when (statusKind) {
            StatusKind.SUCCESS -> AccentGreen
            StatusKind.ERROR -> AccentRed
            StatusKind.IDLE -> TextMuted
        }
        val messageColor = when (statusKind) {
            StatusKind.SUCCESS -> AccentGreen
            StatusKind.ERROR -> AccentRed
            StatusKind.IDLE -> TextSecondary
        }
        Box(
            modifier = Modifier
                .size(size = 8.dp)
                .background(color = dot, shape = CircleShape),
        )
        Text(
            text = status,
            style = MaterialTheme.typography.caption,
            color = messageColor,
        )
        Spacer(modifier = Modifier.weight(weight = 1f))
        Text(
            text = session?.let { project ->
                buildString {
                    if (isDirty) append("• ")
                    append(project.displayName)
                    append(" — ")
                    append(project.root)
                    append(" • ${project.ruleFiles.size} rule file(s)")
                    if (project.schemaLink?.let(ProjectPaths::isExternal) == true) append(" • shared schema")
                }
            } ?: "Unsaved project — never saved",
            style = MaterialTheme.typography.caption,
            color = if (isDirty) AccentOrange else TextMuted,
        )
        parsedSchema?.let {
            Text(
                text = "Schema: ${it.fields.size} fields",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}
