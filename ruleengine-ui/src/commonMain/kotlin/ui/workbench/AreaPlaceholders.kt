package ui.workbench

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ui.TextSecondary

/**
 * Placeholder shown when the Schema area is active but its visual editor is not yet implemented.
 */
@Composable
fun SchemaAreaPlaceholder(modifier: Modifier = Modifier) {
    AreaPlaceholder(
        label = "Field Schema Editor",
        sublabel = "Visual editor coming in Phase 5. Use the YAML mode to edit schema text.",
        modifier = modifier,
    )
}

/**
 * Placeholder shown when the Actions area is active but its visual editor is not yet implemented.
 */
@Composable
fun ActionsAreaPlaceholder(modifier: Modifier = Modifier) {
    AreaPlaceholder(
        label = "Action Schema Editor",
        sublabel = "Visual editor coming in Phase 6. Use the YAML mode to edit action schema text.",
        modifier = modifier,
    )
}

/**
 * Placeholder shown when the Manifest area is active but its builder is not yet implemented.
 */
@Composable
fun ManifestAreaPlaceholder(modifier: Modifier = Modifier) {
    AreaPlaceholder(
        label = "Manifest Builder",
        sublabel = "Builder coming in Phase 7. Use the YAML mode to edit manifest text.",
        modifier = modifier,
    )
}

@Composable
private fun AreaPlaceholder(
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$label\n$sublabel",
            style = MaterialTheme.typography.body1,
            color = TextSecondary,
        )
    }
}
