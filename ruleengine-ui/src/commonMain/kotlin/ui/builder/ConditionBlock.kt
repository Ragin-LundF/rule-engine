package ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary

/** Renders a single condition row as [field] [operator] [value] chips. */
@Composable
fun ConditionBlock(condition: BuilderCondition, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConditionChip(text = condition.field, style = ChipStyle.FIELD)
        ConditionChip(text = condition.operator, style = ChipStyle.OPERATOR)
        ConditionChip(text = condition.value, style = ChipStyle.VALUE)
    }
}

private enum class ChipStyle { FIELD, OPERATOR, VALUE }

@Composable
private fun ConditionChip(text: String, style: ChipStyle) {
    val bg = when (style) {
        ChipStyle.FIELD -> BgElevated
        ChipStyle.OPERATOR -> AccentGreen
        ChipStyle.VALUE -> BgElevated
    }
    val textColor = when (style) {
        ChipStyle.FIELD -> PrimaryBlue
        ChipStyle.OPERATOR -> MaterialTheme.colors.onSecondary
        ChipStyle.VALUE -> TextPrimary
    }
    val borderColor = when (style) {
        ChipStyle.FIELD -> BorderColor
        ChipStyle.OPERATOR -> AccentGreen
        ChipStyle.VALUE -> BorderColor
    }
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = textColor,
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
