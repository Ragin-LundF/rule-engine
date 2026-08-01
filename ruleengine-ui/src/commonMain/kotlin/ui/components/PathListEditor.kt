package ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue

/**
 * Editable list of file paths with add/remove controls.
 */
@Composable
fun PathListEditor(
    paths: List<String>,
    onPathsChange: (List<String>) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        paths.forEachIndexed { index, path ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { newPath ->
                        val updated = paths.toMutableList().also { it[index] = newPath }
                        onPathsChange(updated)
                    },
                    label = { Text("$label ${index + 1}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        val updated = paths.toMutableList().also { it.removeAt(index) }
                        onPathsChange(updated)
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colors.error)
                }
            }
        }
        TextButton(
            onClick = { onPathsChange(paths + "") },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("+ Add $label", color = PrimaryBlue)
        }
    }
}
