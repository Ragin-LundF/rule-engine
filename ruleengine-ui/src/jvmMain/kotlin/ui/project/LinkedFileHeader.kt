package ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.Bg
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.components.StatusBadge
import ui.components.ToolbarButton

/**
 * Shows which file the schema or actions editor is bound to, with the actions that change it.
 *
 * This is where linking belongs rather than in the toolbar: replacing the schema is a statement
 * about *this* file, and the user needs to see what they are replacing — especially when it is a
 * shared file that other projects also read.
 */
@Composable
fun LinkedFileHeader(
    label: String,
    linkedPath: String?,
    isMissing: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = Bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.overline,
                color = TextSecondary,
            )
            Text(
                text = linkedPath ?: "not linked",
                style = MaterialTheme.typography.body2,
                color = if (linkedPath == null) TextSecondary else TextPrimary,
            )
        }

        when {
            isMissing -> StatusBadge(label = "NOT FOUND", color = MaterialTheme.colors.error)
            linkedPath != null && ProjectPaths.isExternal(relativePath = linkedPath) ->
                StatusBadge(label = "SHARED", color = PrimaryBlue)
        }

        Spacer(modifier = Modifier.weight(weight = 1f))

        ToolbarButton(
            label = if (linkedPath == null) "Link file…" else "Change…",
            onClick = onLink,
        )
        if (linkedPath != null) {
            ToolbarButton(label = "Unlink", onClick = onUnlink)
        }
    }
}
