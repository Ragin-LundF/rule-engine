package ui.components.header

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary

/**
 * The one dropdown surface the bars share.
 *
 * There were four copies of this `DropdownMenu` styling — the entry picker, the rule-file menu, the
 * diagram-view picker and the export button — and they had already drifted in corner radius. A menu
 * opened from a bar should look the same wherever it was opened from.
 */
@Composable
fun HeaderMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
        content = content,
    )
}

/** One row of a [HeaderMenu]; the selected one is marked the way the mode tabs mark theirs. */
@Composable
fun HeaderMenuItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.background(
            color = if (selected) BgHover else Color.Transparent,
            shape = RoundedCornerShape(size = 6.dp),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = when {
                !enabled -> TextSecondary
                selected -> PrimaryBlue
                else -> TextPrimary
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** A quiet caption above a group of items — what the group is, not what it does. */
@Composable
fun HeaderMenuSection(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.overline,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** The line between two groups of items. */
@Composable
fun HeaderMenuDivider() {
    Divider(color = BorderColor, thickness = 1.dp)
}
