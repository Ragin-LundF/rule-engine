package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgHover
import ui.PrimaryBlue
import ui.TextOnPrimary
import ui.TextPrimary
import ui.TextSecondary

/**
 * Tab switcher for an editor area's center-panel modes.
 *
 * Shared by the Manifest, Field Schema and Action Schema areas: three strips that differ only in the
 * enum they switch over, and that had already drifted apart in corner radius, text style and label
 * casing while being nominally the same control.
 *
 * [label] rather than a `toString` on the mode, because the display names are the area's to choose:
 * deriving them from the enum constant is what produced "Yaml" next to the other areas' "YAML".
 *
 * @param icon         Optional glyph before the label, so this can replace the Rules area's own
 *                     icon-led toggle rather than leaving two controls for one job.
 * @param showLabels   False renders the icons alone, which is how the strip survives a narrow panel.
 *                     Ignored when there is no [icon] to fall back on — a wordless, iconless tab would
 *                     be an unlabelled click target.
 * @param showIcons    False drops the glyphs and keeps the words, which is the *middle* step for a
 *                     wide strip: five labelled tabs do not fit a narrow panel, but the words are worth
 *                     more than the glyphs, so the icons go first and the labels only after them.
 * @param subordinate  Styles the strip as a switch *within* the current mode rather than a switch of
 *                     modes: no container, and the selected item is merely raised instead of accented.
 *                     The Outline/Board canvas switch is the one that needs this — it changes how a
 *                     rule is drawn, not what the centre panel is, and it must not read as a mode tab.
 */
@Composable
fun <T> ModeTabs(
    modes: List<T>,
    current: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ((T) -> String)? = null,
    showLabels: Boolean = true,
    showIcons: Boolean = true,
    subordinate: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (subordinate) Color.Transparent else BgElevated)
            .padding(all = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEach { mode ->
            ModeTab(
                text = tabText(
                    mode = mode,
                    label = label,
                    icon = icon.takeIf { showIcons },
                    showLabels = showLabels,
                ),
                // The plain name, whatever is drawn: an icon-only tab is otherwise a click target with
                // no name at all — to a screen reader, and to anything else reading the tree.
                name = label(mode),
                selected = mode == current,
                subordinate = subordinate,
                onClick = { onSelect(mode) },
            )
        }
    }
}

/**
 * One tab.
 *
 * The selected label is [TextOnPrimary], not [TextPrimary]: the accent fill behind it is the same
 * colour in both themes, so a text colour that follows the theme is legible in one and nearly
 * invisible in the other.
 */
@Composable
private fun ModeTab(
    text: String,
    name: String,
    selected: Boolean,
    subordinate: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected && subordinate -> BgHover
        selected -> PrimaryBlue
        subordinate -> Color.Transparent
        else -> BgHover
    }
    val foreground = when {
        selected && subordinate -> TextPrimary
        selected -> TextOnPrimary
        else -> TextSecondary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = foreground,
        // A tab that wraps is the "Validate rendered one letter per line" bug in a smaller box: the
        // strip is the fixed part of a header, so it must reach its width or be given fewer words,
        // never fold.
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .semantics { contentDescription = name }
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** What one tab reads, given what the caller supplied and how much room the strip has. */
private fun <T> tabText(mode: T, label: (T) -> String, icon: ((T) -> String)?, showLabels: Boolean): String {
    val glyph = icon?.invoke(mode)
    return when {
        glyph == null -> label(mode)
        showLabels -> "$glyph ${label(mode)}"
        else -> glyph
    }
}
