package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.AccentPurple
import ui.AccentPurpleSoft
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary

/**
 * A compact chip representing a field from the schema catalog.
 */
@Composable
fun FieldChip(
    fieldId: String,
    typeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val bg = if (selected) PrimaryGlow else BgElevated
    val border = if (selected) PrimaryBlue.copy(alpha = 0.5f) else BorderColor

    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = bg)
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fieldId,
            style = MaterialTheme.typography.body2,
            color = if (selected) PrimaryBlue else TextPrimary,
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
    val bg = if (selected) AccentPurpleSoft else BgElevated
    val border = if (selected) AccentPurple.copy(alpha = 0.5f) else BorderColor

    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = bg)
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = actionName,
            style = MaterialTheme.typography.body2,
            color = if (selected) AccentPurple else TextPrimary,
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
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = color.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.35f),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = color,
        )
    }
}

/**
 * A tiny read-only info chip for tables and lists.
 */
@Composable
fun InfoChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgHover)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
