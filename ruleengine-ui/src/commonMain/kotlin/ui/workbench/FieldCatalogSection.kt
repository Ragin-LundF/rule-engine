package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.FieldChip
import ui.components.SectionTitle

/**
 * A compact, scrollable list of field chips derived from the loaded schema.
 * Clicking a chip notifies the caller so the inspector can be updated.
 */
@Composable
fun FieldCatalogSection(
    fields: List<CatalogField>,
    selectedFieldId: String?,
    onFieldClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(text = "FIELDS")
        fields.forEach { field ->
            FieldChip(
                fieldId = field.id,
                typeLabel = field.type,
                selected = field.id == selectedFieldId,
                onClick = { onFieldClick(field.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A field entry derived from the loaded schema for display in the catalog.
 */
data class CatalogField(
    val id: String,
    val type: String,
    val operators: List<String> = emptyList(),
    val normalizers: List<String> = emptyList(),
    val alias: String? = null,
)
