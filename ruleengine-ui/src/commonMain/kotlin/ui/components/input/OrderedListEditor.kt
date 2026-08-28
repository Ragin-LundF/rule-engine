package ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.components.row.PlainTextField
import ui.components.input.model.OrderedListOption

/**
 * A list whose **order is part of its meaning**, edited as an order.
 *
 * Two things in the schema format are ordered lists, and both were rendered as unordered chip rows —
 * which is not a smaller version of this control, it is a different type:
 *
 * - a field's `normalizers` is a **chain**. `NormalizerRegistry.applyAll` runs it left to right and its
 *   own documentation gives the counter-example: `trim` then `lowercase` is not the same chain as the
 *   other way round once `collapse_whitespace` is between them. Ticking chips left the order as
 *   whatever order the boxes happened to be clicked, invisible and unsettable.
 * - an action's `argTypes` is a **positional parameter list**. `Validator` checks the argument count and
 *   then the type at each index, so `audit(string, integer)` and `audit(integer, string)` are different
 *   declarations — and `audit(string, string)` was unreachable entirely, because a chip is either on or
 *   off.
 *
 * Hence [allowDuplicates]: a chain may not repeat a step, a parameter list may.
 *
 * Narrow panels move the row's controls to a second line rather than squeezing the value — the panel
 * this lives in is draggable from 260dp to 720dp, so neither end can be the only one that works.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun OrderedListEditor(
    items: List<String>,
    options: List<OrderedListOption>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onAdd: (value: String) -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowDuplicates: Boolean = false,
    /** What each position is called — "1", "2" … by default; an argument list says "arg 1". */
    positionLabel: (index: Int) -> String = { index -> (index + 1).toString() },
    /**
     * Supplied when a position holds free text rather than a value picked from [options] — a file path
     * is the case, and it is why the rule files were unreachable by keyboard: with no editor on the row
     * and nothing to offer as an option, a wrong path could only be removed and re-added.
     */
    onEdit: ((index: Int, value: String) -> Unit)? = null,
    /** An extra control on each row, to the right of its value — the path picker uses it. */
    rowAction: (@Composable (index: Int) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stacked = maxWidth < STACK_BELOW

        Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            if (items.isEmpty()) {
                Text(text = emptyText, style = MaterialTheme.typography.caption, color = TextSecondary)
            }

            items.forEachIndexed { index, value ->
                OrderedRow(
                    position = positionLabel(index),
                    value = value,
                    what = options.firstOrNull { option -> option.value == value }?.what.orEmpty(),
                    stacked = stacked,
                    enabled = enabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < items.lastIndex,
                    onUp = { onMove(index, index - 1) },
                    onDown = { onMove(index, index + 1) },
                    onRemove = { onRemove(index) },
                    onEdit = onEdit?.let { edit -> { text: String -> edit(index, text) } },
                    rowAction = rowAction?.let { action -> { action(index) } },
                )
            }

            val addable = if (allowDuplicates) options else options.filter { it.value !in items }
            if (addable.isNotEmpty() && enabled) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    addable.forEach { option ->
                        AddChip(label = option.value, onClick = { onAdd(option.value) })
                    }
                }
            }
        }
    }
}

/** One position: its number, its value, and what can be done to it. */
@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
private fun OrderedRow(
    position: String,
    value: String,
    what: String,
    stacked: Boolean,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    onEdit: ((String) -> Unit)? = null,
    rowAction: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            PositionBadge(text = position)
            Column(modifier = Modifier.weight(weight = 1f)) {
                if (onEdit == null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
                        color = TextPrimary,
                    )
                } else {
                    PlainTextField(
                        value = value,
                        placeholder = "path",
                        onValueChange = onEdit,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (what.isNotBlank()) {
                    Text(text = what, style = MaterialTheme.typography.caption, color = TextSecondary)
                }
            }
            rowAction?.invoke()
            if (!stacked) {
                RowControls(
                    enabled = enabled,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onUp = onUp,
                    onDown = onDown,
                    onRemove = onRemove,
                )
            }
        }
        if (stacked) {
            RowControls(
                enabled = enabled,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onUp = onUp,
                onDown = onDown,
                onRemove = onRemove,
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RowControls(
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        IconAction(glyph = "↑", enabled = enabled && canMoveUp, onClick = onUp)
        IconAction(glyph = "↓", enabled = enabled && canMoveDown, onClick = onDown)
        IconAction(glyph = "×", enabled = enabled, onClick = onRemove)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PositionBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
        color = PrimaryBlue,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = PrimaryBlue.copy(alpha = BADGE_ALPHA))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * A move or remove control.
 *
 * Disabled rather than hidden at the ends of the list: a control that disappears makes the row jump
 * width as the selection moves down it, and the reader then has to work out whether it vanished
 * because the gesture is impossible or because they mis-aimed.
 */
@Suppress("FunctionNaming")
@Composable
private fun IconAction(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = glyph,
        style = MaterialTheme.typography.caption,
        color = if (enabled) TextSecondary else TextMuted.copy(alpha = DISABLED_ALPHA),
        modifier = Modifier
            .size(size = TOUCH_TARGET)
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(all = 4.dp),
    )
}

@Suppress("FunctionNaming")
@Composable
private fun AddChip(label: String, onClick: () -> Unit) {
    Text(
        text = "+ $label",
        style = MaterialTheme.typography.caption,
        color = PrimaryBlue,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .border(
                width = 1.dp,
                color = PrimaryBlue.copy(alpha = BORDER_ALPHA),
                shape = RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Below this the controls take their own line rather than squeezing the value beside them. */
private val STACK_BELOW: Dp = 260.dp

private val TOUCH_TARGET: Dp = 22.dp
private const val BADGE_ALPHA: Float = 0.18f
private const val BORDER_ALPHA: Float = 0.45f
private const val DISABLED_ALPHA: Float = 0.5f
