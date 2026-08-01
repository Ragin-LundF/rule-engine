package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.builder.OperatorOptions
import ui.components.SectionTitle
import ui.workbench.model.catalog.CatalogField

/**
 * Inspector for a selected field schema definition.
 */
@Composable
fun FieldInspector(
    field: CatalogField,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "FIELD")
        Text(
            text = field.id,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
        )
        Divider()

        InspectorRow(label = "Type", value = field.type)
        InspectorRow(label = "Path", value = field.id)
        if (!field.alias.isNullOrBlank()) {
            InspectorRow(label = "Alias", value = field.alias)
        }
        InspectorRow(label = "Operators", value = chipSummary(items = field.operators))
        InspectorRow(label = "Normalizers", value = chipSummary(items = field.normalizers))
        InspectorRow(label = "Usages", value = "0 rules") // TODO compute usages in later phase

        Divider()
        SectionTitle(text = "EXAMPLE")
        val exampleOp = field.operators.firstOrNull() ?: OperatorOptions.EQUALS
        Text(
            text = "${field.id} $exampleOp <value>",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.primary,
        )

        Divider()
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { /* TODO open schema editor for this field */ },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Edit field", style = MaterialTheme.typography.caption)
            }
        }
    }
}

private fun chipSummary(items: List<String>, maxVisible: Int = 2): String {
    if (items.isEmpty()) return "none"
    val visible = items.take(maxVisible)
    val remaining = items.size - visible.size
    return if (remaining > 0) {
        visible.joinToString(", ") + ", +$remaining"
    } else {
        visible.joinToString(", ")
    }
}
