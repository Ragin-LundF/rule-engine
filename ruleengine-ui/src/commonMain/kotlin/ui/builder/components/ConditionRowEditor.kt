package ui.builder.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.CatalogFieldInfo
import ui.builder.MutableBuilderCondition
import ui.builder.OperatorOptions
import ui.components.TinyButton

/**
 * A single editable condition row: field, operator, typed value, and remove button.
 */
@Composable
fun ConditionRowEditor(
    condition: MutableBuilderCondition,
    fields: List<CatalogFieldInfo>,
    onSelected: () -> Unit,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
    onSwitchToAdvanced: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fieldInfo = fields.firstOrNull { it.id == condition.field }
    val operators = OperatorOptions.forField(
        fieldType = fieldInfo?.type ?: "text",
        schemaOperators = fieldInfo?.operators ?: emptyList(),
    )
    // Only text-ish comparisons can be case-insensitive; the engine ignores the flag elsewhere.
    val supportsIgnoreCase = fieldInfo == null ||
        fieldInfo.type.lowercase() == "text" ||
        fieldInfo.type.lowercase() == "string_set"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(weight = 1f)
                .clickable(onClick = onSelected),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⠿",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            FieldDropdown(
                selectedFieldId = condition.field,
                fields = fields,
                onFieldSelected = { selectedField ->
                    condition.field = selectedField.id
                    val newOperators = OperatorOptions.forField(
                        fieldType = selectedField.type,
                        schemaOperators = selectedField.operators,
                    )
                    if (condition.operator !in newOperators) {
                        condition.operator = newOperators.firstOrNull() ?: condition.operator
                    }
                    onChanged()
                },
                modifier = Modifier.width(width = 150.dp),
            )

            OperatorDropdown(
                selectedOperator = condition.operator,
                operators = operators,
                onOperatorSelected = { selectedOperator ->
                    condition.operator = selectedOperator
                    onChanged()
                },
                modifier = Modifier.width(width = 120.dp),
            )

            TypedValueEditor(
                condition = condition,
                onChanged = onChanged,
                fieldType = fieldInfo?.type ?: "text",
                modifier = Modifier.weight(weight = 1f),
            )

            if (supportsIgnoreCase) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = condition.ignoreCase,
                        onCheckedChange = {
                            condition.ignoreCase = it
                            onChanged()
                        },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
                    )
                    Text(
                        text = "ignore case",
                        style = MaterialTheme.typography.caption,
                        color = TextSecondary,
                    )
                }
            }
        }

        // Escape hatch into a comparison row, where a side can become an aggregate or calculation.
        if (onSwitchToAdvanced != null) {
            TinyButton(
                text = "ƒ",
                onClick = onSwitchToAdvanced,
            )
        }

        TinyButton(
            text = "×",
            onClick = onRemove,
        )
    }
}
