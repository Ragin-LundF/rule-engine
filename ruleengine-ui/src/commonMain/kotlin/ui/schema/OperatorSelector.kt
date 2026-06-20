package ui.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Renders a row of toggle chips for each known operator.
 * Selected operators are highlighted; clicking toggles selection.
 *
 * @param selected    Currently selected operator ids.
 * @param onToggle    Called with the operator id when the user clicks a chip.
 * @param enabled     When false all chips are non-interactive (read-only mode).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KnownOperators.forEach { op ->
            FilterChip(
                selected = op in selected,
                onClick = { if (enabled) onToggle(op) },
                modifier = Modifier.padding(vertical = 2.dp),
            ) {
                Text(text = op)
            }
        }
    }
}
