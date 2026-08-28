package ui.components.header

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.Bg
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.components.StatusBadge
import ui.components.header.model.BadgeTone
import ui.components.header.model.BarDensity
import ui.components.header.model.BindingMenuItem
import ui.components.header.model.BindingSpec

/**
 * What an area is bound to, and the menu that changes it.
 *
 * The value truncates before anything else in the header does: a long path is the one piece of a bar
 * that can be arbitrarily long, and letting it push the mode tabs off the edge is how a header stops
 * being a header.
 *
 * A [BindingSpec] with no items renders as a plain marker with no caret. That is the read-only case —
 * a loaded sample has an entry to name but no project behind it, and a menu offering two controls that
 * do nothing is worse than no menu.
 */
@Composable
fun BindingChip(
    spec: BindingSpec,
    onItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    density: BarDensity = BarDensity.FULL,
) {
    var expanded by remember { mutableStateOf(value = false) }
    val hasMenu = spec.items.isNotEmpty()

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 7.dp))
                .background(color = Bg)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 7.dp))
                .clickable(enabled = hasMenu, onClick = { expanded = true })
                .widthIn(min = 96.dp, max = maxChipWidth(density = density))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 7.dp),
        ) {
            if (density != BarDensity.MINIMAL) {
                Text(
                    text = spec.label.uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = TextMuted,
                )
            }
            Text(
                text = spec.value,
                style = MaterialTheme.typography.body2,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The value gives way before the key, the badge or the caret do: those are fixed-size
                // and meaningless when clipped, while a truncated path still reads as a path.
                modifier = Modifier.weight(weight = 1f, fill = false),
            )
            spec.badge?.let { badge ->
                StatusBadge(label = badge.text.uppercase(), color = toneColor(tone = badge.tone))
            }
            if (hasMenu) {
                // `▼` and the brighter colour, because the smaller `▾` in a muted grey is invisible at
                // this size — and the caret is the only thing saying this chip opens.
                Text(text = "▼", style = MaterialTheme.typography.caption, color = TextSecondary)
            }
        }

        HeaderMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            spec.items.forEach { item ->
                BindingMenuEntry(
                    item = item,
                    onClick = {
                        expanded = false
                        onItem(item.id)
                    },
                )
            }
        }
    }
}

/** One menu row, with whatever separates it from the row above. */
@Composable
private fun BindingMenuEntry(item: BindingMenuItem, onClick: () -> Unit) {
    if (item.separatorBefore) {
        HeaderMenuDivider()
    }
    item.sectionTitle?.let { title ->
        HeaderMenuSection(title = title)
    }
    HeaderMenuItem(label = item.label, onClick = onClick, selected = item.selected)
}

/**
 * How much of the bar the chip may take.
 *
 * A ceiling per density rather than a weight: a weighted chip in a bar whose other children already
 * fill it is measured at zero width and disappears altogether, which is the one thing a control that
 * names the open file must never do. A ceiling truncates instead, and truncation is legible.
 */
private fun maxChipWidth(density: BarDensity): Dp {
    return when (density) {
        BarDensity.FULL -> 280.dp
        BarDensity.COMPACT -> 190.dp
        BarDensity.MINIMAL -> 140.dp
    }
}

/** The chip owns the colour, so a caller cannot invent a fourth meaning for a badge. */
@Composable
private fun toneColor(tone: BadgeTone): Color {
    return when (tone) {
        BadgeTone.INFO -> PrimaryBlue
        BadgeTone.WARNING -> AccentOrange
        BadgeTone.ERROR -> MaterialTheme.colors.error
    }
}
