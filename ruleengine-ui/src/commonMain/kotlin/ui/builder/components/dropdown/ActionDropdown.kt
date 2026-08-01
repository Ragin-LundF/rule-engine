package ui.builder.components.dropdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.builder.model.catalog.CatalogActionInfo

/**
 * Dropdown for selecting an action from the action schema catalog.
 */
@Composable
fun ActionDropdown(
    selectedAction: String,
    actions: List<CatalogActionInfo>,
    onActionSelected: (CatalogActionInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        selected = selectedAction,
        options = actions.map { it.name },
        onSelected = { selectedName ->
            val action = actions.firstOrNull { it.name == selectedName }
            if (action != null) {
                onActionSelected(action)
            }
        },
        modifier = modifier,
        placeholder = "action",
    )
}
