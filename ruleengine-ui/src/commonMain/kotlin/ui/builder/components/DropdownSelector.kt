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
import androidx.compose.ui.unit.sp
import ui.BgElevated
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextPrimary
import ui.TextSecondary

/**
 * A modern, clearly clickable dropdown trigger that opens a menu with [options].
 * The chevron icon is large and high-contrast so the control is unmistakably a dropdown.
 */
@Composable
fun DropdownSelector(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select...",
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected.ifBlank { placeholder }

    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .clickable(onClick = { expanded = true })
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = if (selected.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (selected.isNotBlank()) TextPrimary else TextSecondary,
            )
            Text(
                text = "▼",
                fontSize = 12.sp,
                color = PrimaryBlue,
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            ),
    ) {
        options.forEach { option ->
            val selectedOption = option == selected
            DropdownMenuItem(
                onClick = {
                    onSelected(option)
                    expanded = false
                },
                modifier = Modifier
                    .background(
                        color = if (selectedOption) BgHover else BgElevated,
                        shape = RoundedCornerShape(size = 6.dp),
                    ),
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.body2,
                    color = if (selectedOption) PrimaryBlue else TextPrimary,
                    fontWeight = if (selectedOption) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
