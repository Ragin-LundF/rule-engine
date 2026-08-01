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
import ui.builder.model.catalog.CatalogActionInfo
import ui.components.SectionTitle

/**
 * Inspector for a selected action schema definition.
 */
@Composable
fun ActionInspector(
    action: CatalogActionInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "ACTION")
        Text(
            text = action.name,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
        )
        Divider()

        InspectorRow(label = "Argument type", value = action.argType)
        InspectorRow(label = "Usages", value = "0 rules") // TODO compute usages in later phase

        Divider()
        SectionTitle(text = "EXAMPLE")
        Text(
            text = "then ${action.name} <${action.argType}>",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.primary,
        )

        Divider()
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { /* TODO open actions editor for this action */ },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "Edit action", style = MaterialTheme.typography.caption)
            }
        }
    }
}
