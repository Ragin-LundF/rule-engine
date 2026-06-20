package ui.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.ToggleChip

/**
 * Renders a row of toggle chips for each known operator.
 * Selected operators are highlighted with a bright green fill so they
 * are never mistaken for unselected grey chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
fun OperatorSelector(
    selected: List<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KnownOperators.forEach { op ->
            ToggleChip(
                label = op,
                selected = op in selected,
                onClick = { onToggle(op) },
                enabled = enabled,
            )
        }
    }
}
