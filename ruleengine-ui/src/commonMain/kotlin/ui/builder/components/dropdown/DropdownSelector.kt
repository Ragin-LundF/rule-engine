package ui.builder.components.dropdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentOrange
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary

/**
 * A clearly clickable dropdown.
 * - The trigger looks like a real select box with the chevron pinned to the far right.
 * - The menu opens directly below the trigger (inside the same anchor Box).
 *
 * A [selected] value that is not among [options] is not silently presented as a valid choice: it is
 * marked as unknown and kept in the menu, so the user can see that the current value is off-list and
 * re-selecting it stays a no-op instead of being impossible.
 */
@Composable
fun DropdownSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected.ifBlank { placeholder }
    val unknown = selected.isNotBlank() && selected !in options

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(size = 8.dp))
                .background(color = BgElevated)
                .border(
                    width = 1.dp,
                    color = BorderColor,
                    shape = RoundedCornerShape(size = 8.dp),
                )
                .clickable(onClick = { expanded = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (unknown) "$label $UNKNOWN_MARKER" else label,
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = if (selected.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                ),
                color = when {
                    unknown -> AccentOrange
                    selected.isNotBlank() -> TextPrimary
                    else -> TextSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(weight = 1f, fill = false),
            )
            Text(
                text = "▼",
                fontSize = 14.sp,
                color = PrimaryBlue,
            )
        }

        OptionMenu(
            expanded = expanded,
            options = options,
            selected = selected,
            onSelected = onSelected,
            onDismiss = { expanded = false },
        )
    }
}

/** Marker appended to a value the schema does not declare, matching `ui.schema.OperatorSelector`. */
internal const val UNKNOWN_MARKER: String = "⚠"

/**
 * The option list shared by [DropdownSelector] and [PathSegmentPill], so both mark an off-list value
 * the same way and neither can drop it.
 */
@Composable
internal fun OptionMenu(
    expanded: Boolean,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // The current value leads the menu when it is off-list: it must stay selectable, but visibly so.
    val entries = if (selected.isNotBlank() && selected !in options) listOf(selected) + options else options

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            ),
    ) {
        entries.forEach { option ->
            val isSelected = option == selected
            val declared = option in options
            DropdownMenuItem(
                onClick = {
                    onSelected(option)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isSelected) BgHover else BgElevated,
                        shape = RoundedCornerShape(size = 6.dp),
                    ),
            ) {
                Text(
                    text = if (declared) option else "$option $UNKNOWN_MARKER",
                    style = MaterialTheme.typography.body2,
                    color = when {
                        !declared -> AccentOrange
                        isSelected -> PrimaryBlue
                        else -> TextPrimary
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
