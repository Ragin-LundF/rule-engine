package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.ActionChip
import ui.components.SectionTitle

/**
 * A compact list of action chips derived from the loaded action schema.
 * Clicking a chip notifies the caller so the inspector can be updated.
 */
@Composable
fun ActionCatalogSection(
    actions: List<CatalogAction>,
    selectedActionName: String?,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(text = "ACTIONS")
        actions.forEach { action ->
            ActionChip(
                actionName = action.name,
                argTypeLabel = action.argType,
                selected = action.name == selectedActionName,
                onClick = { onActionClick(action.name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * An action entry derived from the loaded action schema for display in the catalog.
 */
data class CatalogAction(
    val name: String,
    val argType: String,
)
