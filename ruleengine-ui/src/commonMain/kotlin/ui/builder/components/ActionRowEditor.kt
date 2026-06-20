package ui.builder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.builder.CatalogActionInfo
import ui.builder.MutableBuilderAction

/**
 * A single editable action row: action dropdown, argument editor, and remove button.
 */
@Composable
fun ActionRowEditor(
    action: MutableBuilderAction,
    actions: List<CatalogActionInfo>,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionInfo = actions.firstOrNull { it.name == action.name }
    val expectedArgCount = if (actionInfo?.argType == "none") 0 else 1

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionDropdown(
            selectedAction = action.name,
            actions = actions,
            onActionSelected = { selectedAction ->
                action.name = selectedAction.name
                action.arguments.clear()
                if (selectedAction.argType != "none") {
                    action.arguments.add("")
                }
                onChanged()
            },
        )

        if (expectedArgCount > 0) {
            val currentValue = action.arguments.firstOrNull() ?: ""
            OutlinedTextField(
                value = currentValue,
                onValueChange = {
                    if (action.arguments.isEmpty()) {
                        action.arguments.add(it)
                    } else {
                        action.arguments[0] = it
                    }
                    onChanged()
                },
                singleLine = true,
                modifier = Modifier.weight(weight = 1f),
            )
        }

        IconButton(onClick = onRemove) {
            Text(
                text = "×",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
