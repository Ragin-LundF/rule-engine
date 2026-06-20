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
 * Read-only inspector panel for a selected action.
 * Shows argument type and an example snippet.
 */
@Composable
fun ActionInspector(
    action: CatalogAction,
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

        InspectorRow(label = "Arg type", value = action.argType)

        Divider()
        SectionTitle(text = "EXAMPLE")
        Text(
            text = "then ${action.name} <${action.argType}>",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.primary,
        )
    }
}
