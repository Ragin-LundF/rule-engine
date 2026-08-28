package ui.schema

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.components.ModeTabs
import ui.workbench.model.mode.SchemaMode

/**
 * Tab switcher for the Field Schema editor area: Visual / YAML.
 */
@Composable
fun SchemaModeTabs(
    current: SchemaMode,
    onSelect: (SchemaMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModeTabs(
        modes = SchemaMode.entries,
        current = current,
        label = { it.displayName },
        onSelect = onSelect,
        modifier = modifier,
    )
}

private val SchemaMode.displayName: String
    get() = when (this) {
        SchemaMode.VISUAL -> "Visual"
        SchemaMode.YAML -> "YAML"
    }
