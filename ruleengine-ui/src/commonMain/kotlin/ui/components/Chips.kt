package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.AccentPurple
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary

/**
 * A compact chip representing a field from the schema catalog.
 * Shows the field id and its type label.
 */
@Composable
fun FieldChip(
    fieldId: String,
    typeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val bg = if (selected) PrimaryBlue.copy(alpha = 0.15f) else BgHover
    val border = if (selected) PrimaryBlue else BorderColor

    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.small)
            .background(color = bg)
            .border(width = 1.dp, color = border, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fieldId,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface,
        )
        Box(modifier = Modifier.size(width = 6.dp, height = 1.dp))
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}

/**
 * A compact chip representing an action from the action schema catalog.
 */
@Composable
fun ActionChip(
    actionName: String,
    argTypeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val bg = if (selected) AccentPurple.copy(alpha = 0.15f) else BgHover
    val border = if (selected) AccentPurple else BorderColor

    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.small)
            .background(color = bg)
            .border(width = 1.dp, color = border, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = actionName,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface,
        )
        Box(modifier = Modifier.size(width = 6.dp, height = 1.dp))
        Text(
            text = argTypeLabel,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}

/**
 * A small badge chip for status labels (e.g. "valid", "invalid", "draft").
 */
@Composable
fun StatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.small)
            .background(color = color.copy(alpha = 0.15f))
            .border(width = 1.dp, color = color.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = color,
        )
    }
}
