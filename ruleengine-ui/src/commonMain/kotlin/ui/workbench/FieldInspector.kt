package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.SectionTitle

/**
 * Read-only inspector panel for a selected field.
 * Shows type, alias, normalizers, allowed operators, and an example snippet.
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

        if (field.alias != null) {
            InspectorRow(label = "Alias", value = field.alias)
        }

        if (field.normalizers.isNotEmpty()) {
            InspectorRow(label = "Normalizers", value = field.normalizers.joinToString(", "))
        }

        if (field.operators.isNotEmpty()) {
            InspectorRow(label = "Operators", value = field.operators.joinToString(", "))
            Divider()
            SectionTitle(text = "EXAMPLE")
            val exampleOp = field.operators.firstOrNull() ?: "equals"
            Text(
                text = "${field.id} $exampleOp <value>",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary,
            )
        }
    }
}
