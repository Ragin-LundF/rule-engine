package ui.components.input

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.builder.components.row.PlainTextField

/**
 * A file path: typed, or chosen from a dialog.
 *
 * **Typing stays first.** A path in a manifest is often one that does not exist yet — the saver creates
 * `rules/` and `schemas/` — and a dialog cannot offer a file nobody has written. So the text box is the
 * control and [onChoose] is a convenience beside it, never a replacement.
 *
 * [chooseDisabledReason] is the whole reason this is not just a button. A manifest path is relative to
 * the manifest file, so a project that has never been saved has no location for a chosen path to be
 * relative to — and a dialog that returned an absolute path would write something that stops resolving
 * the moment the project moves. Rather than silently doing that, the button greys out, says why on
 * hover, and says why again under the field: a disabled control with no reason is indistinguishable from
 * a broken one, and the hover text alone is invisible to anyone who does not think to hover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun PathField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    /** Puts the label beside the control rather than over it — see `InspectorTextField`. */
    wide: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the platform dialog. Null hides the button, for a caller with no picker to offer. */
    onChoose: (() -> Unit)? = null,
    /** Non-null greys the button out and becomes both its hover text and the line under the field. */
    chooseDisabledReason: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                ChooseButton(onChoose = onChoose, enabled = enabled, reason = chooseDisabledReason)
            }
        } else {
            Text(text = label, style = MaterialTheme.typography.caption, color = TextMuted)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            ) {
                PlainTextField(
                    value = value,
                    placeholder = placeholder,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    modifier = Modifier.weight(weight = 1f),
                )
                ChooseButton(onChoose = onChoose, enabled = enabled, reason = chooseDisabledReason)
            }
        }
        if (onChoose != null && chooseDisabledReason != null) {
            Text(
                text = chooseDisabledReason,
                style = MaterialTheme.typography.caption,
                color = TextMuted,
            )
        }
    }
}

/**
 * The `Choose…` button, disabled with its reason on hover.
 *
 * Disabled rather than hidden, for the reason `OrderedListEditor.IconAction` already records: a control
 * that disappears leaves the reader working out whether the gesture is impossible or they mis-aimed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun ChooseButton(onChoose: (() -> Unit)?, enabled: Boolean, reason: String?) {
    val choose = onChoose ?: return
    val usable = enabled && reason == null

    val button = @Composable {
        Text(
            text = "Choose…",
            style = MaterialTheme.typography.caption,
            color = if (usable) PrimaryBlue else TextMuted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .background(color = BgElevated)
                .border(
                    width = 1.dp,
                    color = if (usable) PrimaryBlue.copy(alpha = BORDER_ALPHA) else BorderColor,
                    shape = RoundedCornerShape(size = 6.dp),
                )
                .clickable(enabled = usable, onClick = choose)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }

    if (reason == null) {
        button()
        return
    }
    TooltipArea(tooltip = { HoverHint(text = reason) }) { button() }
}

@Suppress("FunctionNaming")
@Composable
private fun HoverHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = TextMuted,
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Matches `InspectorParts.LABEL_GUTTER`, so a path field lines up with the fields above it. */
private val LABEL_GUTTER: Dp = 96.dp
private const val BORDER_ALPHA: Float = 0.45f
