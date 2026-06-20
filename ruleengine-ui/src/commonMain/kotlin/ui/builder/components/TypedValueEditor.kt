package ui.builder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.builder.MutableBuilderCondition
import ui.builder.OperatorOptions

/**
 * Value editor that adapts to the selected operator.
 *
 * - Single-value operators show one text field.
 * - `between` shows two text fields (low / high).
 * - List operators show an addable chip list.
 */
@Composable
fun TypedValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        OperatorOptions.isBetween(condition.operator) -> BetweenValueEditor(
            condition = condition,
            onChanged = onChanged,
            modifier = modifier,
        )
        OperatorOptions.isList(condition.operator) -> ListValueEditor(
            condition = condition,
            onChanged = onChanged,
            modifier = modifier,
        )
        else -> SingleValueEditor(
            condition = condition,
            onChanged = onChanged,
            modifier = modifier,
        )
    }
}

@Composable
private fun SingleValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = condition.value,
        onValueChange = {
            condition.value = it
            onChanged()
        },
        singleLine = true,
        modifier = modifier.defaultMinSize(minWidth = 120.dp),
    )
}

@Composable
private fun BetweenValueEditor(
    condition: MutableBuilderCondition,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = condition.value,
            onValueChange = {
                condition.value = it
                onChanged()
            },
            singleLine = true,
            label = { Text(text = "from") },
            modifier = Modifier.width(width = 100.dp),
        )
        Text(text = "..", style = MaterialTheme.typography.body2)
        OutlinedTextField(
            value = condition.valueTo,
            onValueChange = {
                condition.valueTo = it
                onChanged()
            },
            singleLine = true,
            label = { Text(text = "to") },
            modifier = Modifier.width(width = 100.dp),
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
            OutlinedTextField(
                value = newItem,
                onValueChange = { newItem = it },
                singleLine = true,
                label = { Text(text = "value") },
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
