package ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.components.input.model.ReasonedChip

/**
 * A set of choices where some are not allowed, and say so.
 *
 * Built for a field's `operators`, which the engine allows per type: `contains` is a text operator and
 * means nothing on an integer. The selector this replaces appended an off-list operator to the row with
 * a bare `⚠` glued to its label and no explanation, which told the reader that something was wrong
 * without saying what or what to do.
 *
 * Here a blocked value renders dashed and dimmed, its reason is printed under the row, and — this is the
 * part that matters — **it stays clickable while it is selected**, so removing it is one press. A chip
 * that were merely disabled would leave the only way to fix the schema in the YAML tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
fun ReasonedChipRow(
    chips: List<ReasonedChip>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val blocked = chips.filter { chip -> chip.blockedReason != null }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            chips.forEach { chip ->
                Chip(
                    chip = chip,
                    // A blocked value that is not selected cannot be added; a blocked value that *is*
                    // selected must be removable, or the schema cannot be repaired here at all.
                    clickable = enabled && (chip.blockedReason == null || chip.selected),
                    onClick = { onToggle(chip.value) },
                )
            }
        }
        blocked.filter { chip -> chip.selected }.forEach { chip ->
            Text(
                text = "${chip.value}: ${chip.blockedReason}",
                style = MaterialTheme.typography.caption,
                color = AccentOrange,
            )
        }
    }
}

/** A chip's three colours, resolved together so the combinations stay legible. */
private class ChipPaint(val border: Color, val fill: Color, val text: Color)

/**
 * What a chip currently is.
 *
 * Named rather than derived from two booleans at three separate points: the four combinations of
 * "blocked" and "selected" each mean something different, and spelling them out once is what keeps the
 * colour rules readable.
 */
private enum class ChipState { BLOCKED_SELECTED, BLOCKED_INERT, SELECTED, PLAIN }

private fun stateOf(chip: ReasonedChip): ChipState = when {
    chip.blockedReason != null && chip.selected -> ChipState.BLOCKED_SELECTED
    chip.blockedReason != null -> ChipState.BLOCKED_INERT
    chip.selected -> ChipState.SELECTED
    else -> ChipState.PLAIN
}

@Composable
private fun paintFor(chip: ReasonedChip): ChipPaint = when (stateOf(chip = chip)) {
    // In the file and not allowed: the one case that has to be both visible and removable.
    ChipState.BLOCKED_SELECTED -> ChipPaint(
        border = AccentOrange.copy(alpha = BLOCKED_BORDER_ALPHA),
        fill = AccentOrange.copy(alpha = FILL_ALPHA),
        text = AccentOrange,
    )
    // Not allowed and not in the file: shown so the reader can see it exists, offered to nobody.
    ChipState.BLOCKED_INERT -> ChipPaint(
        border = AccentOrange.copy(alpha = BLOCKED_BORDER_ALPHA),
        fill = Color.Transparent,
        text = AccentOrange.copy(alpha = DIMMED_ALPHA),
    )
    ChipState.SELECTED -> ChipPaint(
        border = PrimaryBlue,
        fill = PrimaryBlue.copy(alpha = FILL_ALPHA),
        text = TextPrimary,
    )
    ChipState.PLAIN -> ChipPaint(border = BorderColor, fill = BgElevated, text = TextSecondary)
}

@Suppress("FunctionNaming")
@Composable
private fun Chip(chip: ReasonedChip, clickable: Boolean, onClick: () -> Unit) {
    val paint = paintFor(chip = chip)
    Text(
        text = if (chip.blockedReason != null) "${chip.value} ⚠" else chip.value,
        style = MaterialTheme.typography.caption.copy(
            fontWeight = if (chip.selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = paint.text,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = paint.fill)
            .border(width = 1.dp, color = paint.border, shape = RoundedCornerShape(percent = 50))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

private const val FILL_ALPHA: Float = 0.16f
private const val BLOCKED_BORDER_ALPHA: Float = 0.55f
private const val DIMMED_ALPHA: Float = 0.45f
