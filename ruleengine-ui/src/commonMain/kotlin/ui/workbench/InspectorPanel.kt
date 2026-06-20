package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.components.SectionTitle

/**
 * Top-level inspector panel that delegates to the appropriate sub-inspector
 * based on the currently selected [InspectorItem].
 * When nothing is selected, shows a placeholder prompt.
 */
@Composable
fun InspectorPanel(
    selectedItem: InspectorItem?,
    fields: List<CatalogField>,
    actions: List<CatalogAction>,
    rules: List<CatalogRule>,
    modifier: Modifier = Modifier,
) {
    when (selectedItem) {
        is InspectorItem.Field -> {
            val field = fields.firstOrNull { it.id == selectedItem.id }
            if (field != null) {
                FieldInspector(field = field, modifier = modifier)
            } else {
                InspectorPlaceholder(modifier = modifier)
            }
        }
        is InspectorItem.Action -> {
            val action = actions.firstOrNull { it.name == selectedItem.name }
            if (action != null) {
                ActionInspector(action = action, modifier = modifier)
            } else {
                InspectorPlaceholder(modifier = modifier)
            }
        }
        is InspectorItem.Rule -> {
            val rule = rules.firstOrNull { it.id == selectedItem.id }
            if (rule != null) {
                RuleInspector(rule = rule, modifier = modifier)
            } else {
                InspectorPlaceholder(modifier = modifier)
            }
        }
        null -> InspectorPlaceholder(modifier = modifier)
    }
}

@Composable
private fun InspectorPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle(text = "INSPECTOR")
        Text(
            text = "Select a field, action, or rule to see details.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}

/**
 * A simple two-column label/value row used by all sub-inspectors.
 */
@Composable
internal fun InspectorRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(0.65f),
        )
    }
}
