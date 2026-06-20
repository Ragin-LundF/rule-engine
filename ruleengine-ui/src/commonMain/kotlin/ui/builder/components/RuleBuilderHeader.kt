package ui.builder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue

/**
 * Header for the Builder view: a rule-selector dropdown on the left, a rename
 * button, and an "+ Add rule" button on the right.
 *
 * When [ruleIds] is empty the dropdown shows a placeholder label.
 * Clicking "✎ Rename" switches the selected rule name to an inline text field;
 * pressing Enter or clicking away commits the rename via [onRenameRule].
 */
@Composable
fun RuleBuilderHeader(
    ruleIds: List<String>,
    selectedRuleId: String,
    onRuleSelected: (String) -> Unit,
    onAddRule: () -> Unit,
    onRenameRule: (oldId: String, newId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var renaming by remember(key1 = selectedRuleId) { mutableStateOf(false) }
    var renameText by remember(key1 = selectedRuleId) { mutableStateOf(selectedRuleId) }

    fun commitRename() {
        val trimmed = renameText.trim()
        if (trimmed.isNotBlank() && trimmed != selectedRuleId) {
            onRenameRule(selectedRuleId, trimmed)
        }
        renaming = false
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (renaming) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitRename() }),
                modifier = Modifier.weight(weight = 1f),
            )
            TextButton(onClick = { commitRename() }) {
                Text(
                    text = "✓ OK",
                    color = PrimaryBlue,
                    style = MaterialTheme.typography.button,
                )
            }
        } else {
            val selectPlaceholder = "-- select --"
            DropdownSelector(
                selected = selectedRuleId.ifBlank { selectPlaceholder },
                options = listOf(selectPlaceholder) + ruleIds,
                onSelected = { option ->
                    onRuleSelected(if (option == selectPlaceholder) "" else option)
                },
                placeholder = selectPlaceholder,
                modifier = Modifier.weight(weight = 1f),
            )
            if (selectedRuleId.isNotBlank()) {
                TextButton(onClick = {
                    renameText = selectedRuleId
                    renaming = true
                }) {
                    Text(
                        text = "✎ Rename",
                        color = PrimaryBlue,
                        style = MaterialTheme.typography.button,
                    )
                }
            }
            TextButton(onClick = onAddRule) {
                Text(
                    text = "+ Add rule",
                    color = PrimaryBlue,
                    style = MaterialTheme.typography.button,
                )
            }
        }
    }
}
