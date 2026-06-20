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
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary

/** Renders a single action row as [name] [arg1] [arg2] … chips. */
@Composable
fun ActionBlock(action: BuilderAction, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionChipItem(text = action.name, isName = true)
        action.arguments.forEach { arg ->
            ActionChipItem(text = arg, isName = false)
        }
    }
}

@Composable
private fun ActionChipItem(text: String, isName: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.body2,
        color = if (isName) PrimaryBlue else TextPrimary,
        modifier = Modifier
            .background(color = MaterialTheme.colors.surface, shape = RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
