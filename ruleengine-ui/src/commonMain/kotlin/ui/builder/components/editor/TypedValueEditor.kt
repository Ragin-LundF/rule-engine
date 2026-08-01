package ui.builder.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.builder.OperatorOptions
import ui.builder.components.CompactTextField
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.model.mutable.MutableBuilderCondition

/**
 * Value editor that adapts to the selected operator.
 *
 * - Single-value operators show one text field.
 * - `between` shows two text fields (low / high).
 * - List operators show an addable chip list.
 *
 * @param valueHint shape the value must have, shown as a placeholder — a date field's declared `format`.
 */
@Composable
fun TypedValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    fieldType: String = "text",
    valueHint: String = "",
    modifier: Modifier = Modifier,
) {
    when {
        OperatorOptions.isBetween(condition.operator) -> BetweenValueEditor(
            condition = condition,
            onChanged = onChanged,
            valueHint = valueHint,
            modifier = modifier,
        )
        OperatorOptions.isList(condition.operator) -> ListValueEditor(
            condition = condition,
            onChanged = onChanged,
            modifier = modifier,
        )
        // A boolean has exactly two values, so a dropdown beats free text and cannot be mistyped.
        fieldType.lowercase() == "boolean" -> BooleanValueEditor(
            condition = condition,
            onChanged = onChanged,
            modifier = modifier,
        )
        else -> SingleValueEditor(
            condition = condition,
            onChanged = onChanged,
            valueHint = valueHint,
            modifier = modifier,
        )
    }
}

@Composable
private fun BooleanValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        selected = condition.value,
        options = listOf("true", "false"),
        onSelected = { selected ->
            condition.value = selected
            onChanged()
        },
        modifier = modifier.width(width = 120.dp),
        placeholder = "true / false",
    )
}

@Composable
private fun SingleValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    valueHint: String = "",
    modifier: Modifier = Modifier,
) {
    CompactTextField(
        value = condition.value,
        onValueChange = {
            condition.value = it
            onChanged()
        },
        placeholder = valueHint,
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
    )
}

@Composable
private fun BetweenValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    valueHint: String = "",
    modifier: Modifier = Modifier,
) {
    val boundWidth = if (valueHint.isBlank()) 100.dp else 140.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactTextField(
            value = condition.value,
            onValueChange = {
                condition.value = it
                onChanged()
            },
            placeholder = valueHint.ifBlank { "from" },
            modifier = Modifier.width(width = boundWidth),
        )
        Text(text = "..", style = MaterialTheme.typography.body2)
        CompactTextField(
            value = condition.valueTo,
            onValueChange = {
                condition.valueTo = it
                onChanged()
            },
            placeholder = valueHint.ifBlank { "to" },
            modifier = Modifier.width(width = boundWidth),
        )
    }
}

@Composable
private fun ListValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newItem by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CompactTextField(
                value = newItem,
                onValueChange = { newItem = it },
                placeholder = "value",
                modifier = Modifier.defaultMinSize(minWidth = 120.dp),
            )
            IconButton(onClick = {
                if (newItem.isNotBlank()) {
                    condition.listItems.add(newItem)
                    newItem = ""
                    onChanged()
                }
            }) {
                Text(text = "+")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            condition.listItems.forEach { item ->
                ListItemChip(
                    item = item,
                    onRemove = {
                        condition.listItems.remove(item)
                        onChanged()
                    },
                )
            }
        }
    }
}

@Composable
private fun ListItemChip(
    item: String,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(text = item, style = MaterialTheme.typography.body2)
        IconButton(onClick = onRemove, modifier = Modifier.width(width = 24.dp)) {
            Text(text = "×")
        }
    }
}
