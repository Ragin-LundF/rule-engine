package ui.builder.components.dropdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.builder.model.catalog.CatalogFieldInfo

/**
 * Dropdown for selecting a field from the schema catalog.
 */
@Composable
fun FieldDropdown(
    selectedFieldId: String,
    fields: List<CatalogFieldInfo>,
    onFieldSelected: (CatalogFieldInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        selected = selectedFieldId,
        options = fields.map { it.id },
        onSelected = { selectedId ->
            val field = fields.firstOrNull { it.id == selectedId }
            if (field != null) {
                onFieldSelected(field)
            }
        },
        modifier = modifier,
        placeholder = "field",
    )
}
