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
import ui.builder.model.catalog.CatalogActionInfo
import ui.components.SectionTitle
import ui.util.Plurals

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
        InspectorRow(
            label = "Usages",
            value = "${action.usages} rule${Plurals.suffix(count = action.usages)}",
        )

        Divider()
        SectionTitle(text = "EXAMPLE")
        Text(
            text = "then ${action.name} <${action.argType}>",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.primary,
        )
    }
}
