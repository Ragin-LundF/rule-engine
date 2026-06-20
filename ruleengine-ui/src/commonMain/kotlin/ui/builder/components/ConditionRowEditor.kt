package ui.builder.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.CatalogFieldInfo
import ui.builder.MutableBuilderCondition
import ui.builder.OperatorOptions

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
    modifier: Modifier = Modifier,
) {
    val fieldInfo = fields.firstOrNull { it.id == condition.field }
    val operators = OperatorOptions.forField(
        fieldType = fieldInfo?.type ?: "text",
        schemaOperators = fieldInfo?.operators ?: emptyList(),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
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
        )

        OperatorDropdown(
            selectedOperator = condition.operator,
            operators = operators,
            onOperatorSelected = { selectedOperator ->
                condition.operator = selectedOperator
                onChanged()
            },
        )

        TypedValueEditor(
            condition = condition,
            onChanged = onChanged,
            modifier = Modifier.weight(weight = 1f),
        )

        IconButton(onClick = onRemove) {
            Text(
                text = "×",
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
