package ui.workbench.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.components.row.PlainTextField

/**
 * The pieces every inspector is built from.
 *
 * Shared so the four of them read as one panel rather than four. Each group is separated by a rule and
 * real space: the panel is a stack of unrelated concerns — identity, type, operators, usage — and at
 * ordinary spacing they read as one long form, which is the wrong reading.
 */

/** The inspected thing's name and what kind of thing it is. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorHeading(title: String, kind: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        // Ellipsized, and it must be: at 260dp a dotted member path is wider than the panel, and the
        // badge would otherwise be pushed out of it. The full path is in the crumb trail above.
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1.copy(fontFamily = FontFamily.Monospace),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )
        Text(
            text = kind,
            style = MaterialTheme.typography.caption,
            color = PrimaryBlue,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(shape = RoundedCornerShape(percent = 50))
                .background(color = PrimaryBlue.copy(alpha = CHIP_ALPHA))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** A group heading, with a rule above it so the break reads as deliberate. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorGroup(title: String, note: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(
            color = BorderColor,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(top = GROUP_GAP, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = TextMuted,
            )
            note?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A labelled text field.
 *
 * [wide] moves the label beside its control instead of over it, which is what the extra width is
 * actually good for: fewer lines, rather than looser ones.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun InspectorTextField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    wide: Boolean,
    onValueChange: (String) -> Unit,
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(width = LABEL_GUTTER),
            )
            PlainTextField(
                value = value,
                placeholder = placeholder,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(weight = 1f),
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.caption, color = TextMuted)
        PlainTextField(
            value = value,
            placeholder = placeholder,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The line under a control that says why it is there, or what is wrong. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorNote(text: String, warning: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = if (warning) AccentOrange else TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
    )
}

/** A chip that moves the selection deeper — a member, an argument, a rule file. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorDrillChip(
    label: String,
    note: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
            color = if (enabled) TextPrimary else TextMuted,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = note,
            style = MaterialTheme.typography.caption,
            color = TextMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A dashed add button, matching the Builder's `TinyButton`. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorAddButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.caption,
        color = PrimaryBlue,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .padding(top = 6.dp, bottom = 2.dp)
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .border(
                width = 1.dp,
                color = PrimaryBlue.copy(alpha = BORDER_ALPHA),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** What the file will say about the inspected thing. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorEcho(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private val GROUP_GAP: Dp = 18.dp
private val LABEL_GUTTER: Dp = 96.dp
private const val CHIP_ALPHA: Float = 0.16f
private const val BORDER_ALPHA: Float = 0.45f
