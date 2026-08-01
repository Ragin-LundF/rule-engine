package ui.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.field.FieldType
import ui.components.ToggleChip

/**
 * Renders a row of toggle chips for the operators the engine allows on a [type] field.
 * Selected operators are highlighted with a bright green fill so they
 * are never mistaken for unselected grey chips.
 *
 * An operator already declared in the loaded schema but not valid for [type] is still shown, so nothing
 * disappears silently from an existing schema and the user can untick it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
fun OperatorSelector(
    type: FieldType,
    selected: List<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val supported = operatorsFor(type = type)
    val options = supported + selected.filterNot { it in supported }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { op ->
            ToggleChip(
                label = if (op in supported) op else "$op ⚠",
                selected = op in selected,
                onClick = { onToggle(op) },
                enabled = enabled,
            )
        }
    }
}
