package ui.builder.components

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * A compact outlined button that opens a dropdown menu with [options].
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

    OutlinedButton(
        onClick = { expanded = true },
        modifier = modifier.wrapContentWidth(),
    ) {
        Text(text = label, style = MaterialTheme.typography.body2)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        options.forEach { option ->
            DropdownMenuItem(onClick = {
                onSelected(option)
                expanded = false
            }) {
                Text(text = option)
            }
        }
    }
}
