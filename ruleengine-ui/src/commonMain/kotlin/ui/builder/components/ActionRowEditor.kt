package ui.builder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.builder.CatalogActionInfo
import ui.builder.MutableBuilderAction
import ui.components.TinyButton

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
    val argType = actionInfo?.argType ?: "string"
    val expectedArgCount = if (argType == "none") 0 else 1

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
            modifier = Modifier.width(width = 200.dp),
        )

        if (expectedArgCount > 0) {
            val currentValue = action.arguments.firstOrNull() ?: ""
            ActionValueEditor(
                value = currentValue,
                argType = argType,
                onValueChange = { newValue ->
                    if (action.arguments.isEmpty()) {
                        action.arguments.add(newValue)
                    } else {
                        action.arguments[0] = newValue
                    }
                    onChanged()
                },
                modifier = Modifier.weight(weight = 1f),
            )
        }

        TinyButton(
            text = "×",
            onClick = onRemove,
        )
    }
}

/**
 * Typed value editor for an action argument.
 *
 * - `boolean` → dropdown with true/false options.
 * - All other types → plain text field.
 */
@Composable
private fun ActionValueEditor(
    value: String,
    argType: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (argType == "boolean") {
        DropdownSelector(
            selected = value.ifBlank { "true" },
            options = listOf("true", "false"),
            onSelected = onValueChange,
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = modifier.defaultMinSize(minWidth = 120.dp),
        )
    }
}
