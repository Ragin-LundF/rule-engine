package ui.builder.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dropdown for selecting an operator from the allowed list.
 */
@Composable
fun OperatorDropdown(
    selectedOperator: String,
    operators: List<String>,
    onOperatorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownSelector(
        selected = selectedOperator,
        options = operators,
        onSelected = { selectedOperator ->
            onOperatorSelected(selectedOperator)
        },
        modifier = modifier,
        placeholder = "operator",
    )
}
