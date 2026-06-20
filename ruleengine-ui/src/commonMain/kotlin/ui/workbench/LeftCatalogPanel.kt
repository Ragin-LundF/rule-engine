package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.CatalogSearchField

/**
 * The compact left catalog panel.
 * Shows searchable lists of rules, fields, and actions.
 * Clicking any item fires the corresponding selection callback.
 */
@Composable
fun LeftCatalogPanel(
    rules: List<CatalogRule>,
    fields: List<CatalogField>,
    actions: List<CatalogAction>,
    selectedRuleId: String?,
    selectedInspectorItem: InspectorItem?,
    onRuleClick: (String) -> Unit,
    onFieldClick: (String) -> Unit,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    val filteredRules = remember(rules, query) {
        if (query.isBlank()) rules else rules.filter { it.id.contains(query, ignoreCase = true) }
    }
    val filteredFields = remember(fields, query) {
        if (query.isBlank()) fields else fields.filter { it.id.contains(query, ignoreCase = true) }
    }
    val filteredActions = remember(actions, query) {
        if (query.isBlank()) actions else actions.filter { it.name.contains(query, ignoreCase = true) }
    }

    val selectedFieldId = (selectedInspectorItem as? InspectorItem.Field)?.id
    val selectedActionName = (selectedInspectorItem as? InspectorItem.Action)?.name

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CatalogSearchField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
        )

        if (filteredRules.isNotEmpty()) {
            RuleListSection(
                rules = filteredRules,
                selectedRuleId = selectedRuleId,
                onRuleClick = onRuleClick,
            )
        }

        if (filteredFields.isNotEmpty()) {
            FieldCatalogSection(
                fields = filteredFields,
                selectedFieldId = selectedFieldId,
                onFieldClick = onFieldClick,
            )
        }

        if (filteredActions.isNotEmpty()) {
            ActionCatalogSection(
                actions = filteredActions,
                selectedActionName = selectedActionName,
                onActionClick = onActionClick,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
