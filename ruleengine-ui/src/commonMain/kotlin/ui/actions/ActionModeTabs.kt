package ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.components.ModeTabs
import ui.workbench.model.mode.ActionMode

/**
 * Tab switcher for the Action Schema editor area: Visual / YAML / Usages.
 */
@Composable
fun ActionModeTabs(
    current: ActionMode,
    onSelect: (ActionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModeTabs(
        modes = ActionMode.entries,
        current = current,
        label = { it.displayName },
        onSelect = onSelect,
        modifier = modifier,
    )
}

private val ActionMode.displayName: String
    get() = when (this) {
        ActionMode.VISUAL -> "Visual"
        ActionMode.YAML -> "YAML"
        ActionMode.USAGES -> "Usages"
    }
