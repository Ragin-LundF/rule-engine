package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.BuilderCondition
import ui.builder.OperatorOptions
import ui.components.SectionTitle

/**
 * Inspector for a single selected condition row.
 * Shows field, operator, value details and quick-fix hints.
 */
@Composable
fun ConditionInspector(
    condition: BuilderCondition,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "CONDITION")
        Text(
            text = condition.field,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
        )
        Divider()

        InspectorRow(label = "Operator", value = condition.operator)
        InspectorRow(
            label = "Value type",
            value = when {
                OperatorOptions.isBetween(condition.operator) -> "range"
                OperatorOptions.isList(condition.operator) -> "list"
                else -> "single"
            },
        )
        InspectorRow(
            label = "Current value",
            value = when {
                OperatorOptions.isBetween(condition.operator) ->
                    "${condition.value} .. ${condition.valueTo}"
                OperatorOptions.isList(condition.operator) ->
                    condition.listItems.joinToString(", ")
                else -> condition.value
            },
        )

        Divider()
        SectionTitle(text = "QUICK FIX")
        Text(
            text = "Switch to Code mode to edit unsupported syntax.",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
