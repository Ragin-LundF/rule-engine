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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue

/**
 * Header for the Builder view: the selected rule's id, a rename button, and an
 * "+ Add rule" button on the right.
 *
 * Selection itself is now owned by the rule tree in the left column of Builder mode
 * (see [ui.workbench.RuleTreePanel]), so this header only displays the current selection
 * rather than offering its own picker. [ruleIds] is accepted for source compatibility with
 * the single existing caller but is no longer read here.
 * Clicking "✎ Rename" switches the selected rule name to an inline text field;
 * pressing Enter or clicking away commits the rename via [onRenameRule].
 */
@Suppress("UNUSED_PARAMETER")
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
            Text(
                text = selectedRuleId.ifBlank { "No rule selected" },
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.SemiBold),
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
