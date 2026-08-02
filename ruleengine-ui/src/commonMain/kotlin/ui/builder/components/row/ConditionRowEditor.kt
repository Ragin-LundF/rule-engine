package ui.builder.components.row

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.OperatorOptions
import ui.builder.components.IgnoreCaseToggle
import ui.builder.components.dropdown.FieldDropdown
import ui.builder.components.dropdown.OperatorDropdown
import ui.builder.components.editor.TypedValueEditor
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.scalarPaths
import ui.builder.model.mutable.MutableBuilderCondition
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
    // A condition names one comparable field, so it works over dotted scalar paths rather than the
    // top-level fields: without this a nested schema resolves nothing and every row is marked unknown.
    val fieldOptions = remember(fields) { fields.scalarPaths() }
    val fieldInfo = fieldOptions.firstOrNull { it.id == condition.field }
    val operators = OperatorOptions.forCatalogField(
        fieldId = condition.field,
        fieldType = fieldInfo?.type ?: "text",
        schemaOperators = fieldInfo?.operators ?: emptyList(),
    )
    // Only text-ish comparisons can be case-insensitive; the engine ignores the flag elsewhere. A
    // variable never qualifies: the expression path has no case-insensitive mode, and `ignoreCase`
    // after such a comparison does not parse.
    val supportsIgnoreCase = !OperatorOptions.isVariableId(fieldId = condition.field) &&
        (
            fieldInfo == null ||
                fieldInfo.type.lowercase() == "text" ||
                fieldInfo.type.lowercase() == "string_set"
            )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConditionControls(
            condition = condition,
            fieldOptions = fieldOptions,
            fieldInfo = fieldInfo,
            operators = operators,
            supportsIgnoreCase = supportsIgnoreCase,
            onSelected = onSelected,
            onChanged = onChanged,
            modifier = Modifier.weight(weight = 1f),
        )

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

/**
 * Field, operator and value — the three parts of a simple condition.
 *
 * Changing the field can invalidate the operator (a `text` field has no `>=`), so the operator falls
 * back to the first the new field allows rather than staying on something that will not compile.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ConditionControls(
    condition: MutableBuilderCondition,
    fieldOptions: List<CatalogFieldInfo>,
    fieldInfo: CatalogFieldInfo?,
    operators: List<String>,
    supportsIgnoreCase: Boolean,
    onSelected: () -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onSelected),
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
            fields = fieldOptions,
            onFieldSelected = { selectedField ->
                condition.field = selectedField.id
                val newOperators = OperatorOptions.forCatalogField(
                    fieldId = selectedField.id,
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
            valueHint = fieldInfo?.format ?: "",
            modifier = Modifier.weight(weight = 1f),
        )

        if (supportsIgnoreCase) {
            IgnoreCaseToggle(
                checked = condition.ignoreCase,
                onCheckedChange = {
                    condition.ignoreCase = it
                    onChanged()
                },
            )
        }
    }
}
