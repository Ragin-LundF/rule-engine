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
import ui.TextSecondary
import ui.components.SectionTitle

/**
 * Placeholder inspector for a selected manifest project.
 */
@Composable
fun ManifestInspector(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "MANIFEST")
        Text(
            text = "Manifest",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
        )
        Divider()
        InspectorRow(label = "Schema", value = "unknown")
        InspectorRow(label = "Actions", value = "unknown")
        InspectorRow(label = "Rules", value = "unknown")
        InspectorRow(label = "Missing", value = "none")
        Text(
            text = " Detailed manifest checks will be implemented in Phase 7.",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
