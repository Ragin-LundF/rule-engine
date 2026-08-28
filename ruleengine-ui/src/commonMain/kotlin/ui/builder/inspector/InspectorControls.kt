package ui.builder.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.OperandRules
import ui.builder.OperandText
import ui.builder.model.BuilderOperand
import ui.components.SectionDivider

/**
 * The small controls the builder inspector is built from.
 *
 * Gathered here because the inspector is now the only editing surface: the same labelled field, option
 * list, modifier switch and drill card appear for a condition, an operand, a path segment and a filter,
 * and one definition each is what keeps those four reading as one panel rather than four.
 */

/** A labelled block. The label is the quiet part; the control below it is what the eye should reach. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorField(
    label: String,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary,
            )
            if (hint != null) {
                Spacer(modifier = Modifier.width(width = 6.dp))
                Text(text = hint, style = MaterialTheme.typography.caption, color = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(height = 6.dp))
        content()
    }
}

/** A section rule with a title, used to separate the parts of one target's editor. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorSection(
    title: String,
    hint: String? = null,
    /**
     * Whether to draw the rule that separates this section from what is above it.
     *
     * On by default because every section in the panel today follows something — an echo of the DSL the
     * control will generate, a field, or another section. The flag exists for the one case that would
     * look wrong: a section placed first, where a rule at the top of the panel would have nothing above
     * it to separate from.
     */
    divider: Boolean = true,
) {
    if (divider) {
        SectionDivider()
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = PrimaryBlue,
        )
        if (hint != null) {
            Text(text = hint, style = MaterialTheme.typography.caption, color = TextSecondary)
        }
    }
}

/** Explanatory text: why an option is missing, what a control will generate, what the engine does. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorNote(text: String, warning: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = if (warning) AccentOrange else TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

/** The generated DSL for whatever is selected, so the panel is always verifiable against the text. */
@Suppress("FunctionNaming")
@Composable
internal fun DslEcho(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(all = 8.dp),
    )
}

/**
 * A vertical list of choices, each with an optional reason.
 *
 * Preferred over a dropdown wherever the *set* of choices is part of the explanation — which operators
 * a type allows, what each aggregate means. A dropdown hides exactly the thing the author is trying to
 * learn.
 */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorOptions(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    hints: Map<String, String> = emptyMap(),
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape = RoundedCornerShape(size = 6.dp))
                    .background(color = if (isSelected) PrimaryGlow else BgElevated)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) PrimaryBlue else BorderColor,
                        shape = RoundedCornerShape(size = 6.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) PrimaryBlue else TextPrimary,
                    modifier = Modifier.weight(weight = 1f),
                )
                hints[option]?.let { hint ->
                    Text(text = hint, style = MaterialTheme.typography.caption, color = TextSecondary)
                }
            }
        }
    }
}

/**
 * A modifier switch with its own explanation, and a disabled state that says why.
 *
 * A checkbox that changes nothing reads as one that does, so `ignoreCase` on a number is shown
 * disabled with the reason rather than hidden — the author asked about it by looking.
 */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .then(
                if (enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        val alpha = if (enabled) 1f else 0.45f
        Text(
            text = if (checked) "◉" else "○",
            style = MaterialTheme.typography.body1,
            color = (if (checked) PrimaryBlue else TextSecondary).copy(alpha = alpha),
        )
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.body2,
                color = TextPrimary.copy(alpha = alpha),
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary.copy(alpha = alpha),
                )
            }
        }
    }
}

/**
 * One side of something, shown as its generated text with a way in.
 *
 * This is what replaces inline expansion: the card names the operand and drills to it, so the panel
 * shows one level at a time and the row that owns it never moves.
 */
@Suppress("FunctionNaming")
@Composable
internal fun OperandCard(
    operand: BuilderOperand,
    onDrill: () -> Unit,
    label: String? = null,
) {
    val kind = OperandRules.kindOf(operand = operand)
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(size = 6.dp))
                .background(color = BgElevated)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
                .clickable(onClick = onDrill)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = kind.badge,
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = PrimaryBlue,
            )
            Text(
                text = OperandText.toDsl(operand = operand).ifBlank { "…" },
                style = MaterialTheme.typography.body2,
                color = TextPrimary,
                modifier = Modifier.weight(weight = 1f),
            )
            Text(text = "edit ›", style = MaterialTheme.typography.caption, color = TextSecondary)
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * "What is this side?" — the one control that decides a row's DSL form.
 *
 * There is no mode button anywhere in this panel. Picking a computed kind is what turns a simple
 * condition into a comparison, and making both sides plain again is what turns it back; see
 * `ui.builder.RowForm`. A kind that is out of reach is shown disabled with the reason, because
 * vanishing options are how the old builder made its rules unlearnable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun OperandKindPicker(
    current: OperandRules.OperandKind,
    onSelect: (OperandRules.OperandKind) -> Unit,
    disabledReason: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            OperandRules.OperandKind.entries.forEach { kind ->
                val computed = kind == OperandRules.OperandKind.AGGREGATE ||
                    kind == OperandRules.OperandKind.CALCULATION ||
                    kind == OperandRules.OperandKind.FUNCTION
                val blocked = disabledReason != null && computed
                KindChip(
                    kind = kind,
                    selected = kind == current,
                    blocked = blocked,
                    onClick = { if (!blocked) onSelect(kind) },
                )
            }
        }
        if (disabledReason != null) {
            Spacer(modifier = Modifier.height(height = 6.dp))
            InspectorNote(text = disabledReason, warning = true)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun KindChip(
    kind: OperandRules.OperandKind,
    selected: Boolean,
    blocked: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (blocked) 0.45f else 1f
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = if (selected) PrimaryGlow else BgElevated)
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlue else BorderColor,
                shape = RoundedCornerShape(percent = 50),
            )
            .clickable(enabled = !blocked, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Text(
            text = kind.badge,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = (if (selected) PrimaryBlue else TextSecondary).copy(alpha = alpha),
        )
        Text(
            text = kind.label,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = (if (selected) PrimaryBlue else TextPrimary).copy(alpha = alpha),
        )
    }
}

/** A row of small actions at the foot of an editor — duplicate, remove, change shape. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorActions(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** A hover-highlighted clickable line, used by the crumb trail and the expression outline. */
@Suppress("FunctionNaming")
@Composable
internal fun InspectorLine(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    indent: Int = 0,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = if (selected) PrimaryBlue else TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 4.dp))
            .background(color = if (selected) PrimaryGlow else BgHover.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(start = (indent * 10).dp, top = 3.dp, bottom = 3.dp, end = 4.dp),
    )
}
