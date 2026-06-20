package ui.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.ToggleChip

/**
 * Renders a row of toggle chips for each known normalizer.
 * Selected normalizers are highlighted with a bright green fill so they
 * are never mistaken for unselected grey chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
fun NormalizerSelector(
    selected: List<String>,
    onToggle: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KnownNormalizers.forEach { norm ->
            ToggleChip(
                label = norm,
                selected = norm in selected,
                onClick = { onToggle(norm) },
                enabled = enabled,
            )
        }
    }
}
