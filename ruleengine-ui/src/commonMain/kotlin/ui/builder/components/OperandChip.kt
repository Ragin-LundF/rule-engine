package ui.builder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.BuilderOperand
import ui.builder.CatalogFieldInfo
import ui.builder.OperandRules
import ui.builder.OperandText

/**
 * One side of a comparison, rendered as a chip: a kind badge, the operand's readable label, and a
 * click target that expands the operand's editor.
 *
 * The kind menu opens from the badge and lists only the kinds allowed for this side — aggregates and
 * calculations appear only when the comparison can be numeric (see [OperandRules.availableKinds]).
 */
@Composable
fun OperandChip(
    operand: BuilderOperand,
    otherOperand: BuilderOperand,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onKindChanged: (BuilderOperand) -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(value = false) }
    val kind = OperandRules.kindOf(operand = operand)
    val kinds = OperandRules.availableKinds(other = otherOperand, fields = fields)
    val isEditable = kind == OperandRules.OperandKind.AGGREGATE || kind == OperandRules.OperandKind.CALCULATION

    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = if (expanded) BgHover else BgElevated)
            .border(
                width = 1.dp,
                color = if (expanded) PrimaryBlue else BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Text(
                text = kind.badge,
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = PrimaryBlue,
                modifier = Modifier.clickable(onClick = { menuOpen = true }),
            )

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier
                    .background(color = BgElevated)
                    .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp)),
            ) {
                kinds.forEach { option ->
                    val isSelected = option == kind
                    DropdownMenuItem(
                        onClick = {
                            menuOpen = false
                            if (option != kind) {
                                onKindChanged(
                                    OperandRules.defaultOperand(
                                        kind = option,
                                        fields = fields,
                                        previous = operand,
                                    )
                                )
                            }
                        },
                    ) {
                        Text(
                            text = "${option.badge}  ${option.label}",
                            style = MaterialTheme.typography.body2,
                            color = if (isSelected) PrimaryBlue else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Text(
            text = OperandText.toLabel(operand = operand).ifBlank { "…" },
            style = MaterialTheme.typography.body2,
            color = TextPrimary,
            modifier = Modifier.clickable(onClick = onToggleExpanded),
        )

        if (isEditable) {
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.clickable(onClick = onToggleExpanded),
            )
        }
    }
}
