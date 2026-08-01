package ui.builder.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.BuilderOperand
import ui.builder.CatalogFieldInfo
import ui.builder.OperandText
import ui.builder.OperatorOptions

/**
 * Inline editor for an aggregate operand: the function, the path it aggregates over, and the
 * restrictions on each path segment.
 *
 * The path is delegated to [PathBreadcrumb], so depth is unbounded and a deep or filtered path needs
 * no extra code here.
 */
@Composable
fun AggregateEditor(
    aggregate: BuilderOperand.Aggregate,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand.Aggregate) -> Unit,
    modifier: Modifier = Modifier,
) {
    TitledPanelCard(
        title = "Aggregate",
        detail = OperandText.toDsl(operand = aggregate),
        modifier = modifier,
    ) {
        LabelledRow(label = "function") {
            DropdownSelector(
                selected = aggregate.function,
                options = OperatorOptions.AGGREGATE_FUNCTIONS,
                onSelected = { onChanged(aggregate.copy(function = it)) },
                modifier = Modifier.width(width = 130.dp),
            )
        }

        LabelledRow(label = "over") {
            PathBreadcrumb(
                path = aggregate.path,
                fields = fields,
                onPathChanged = { onChanged(aggregate.copy(path = it)) },
            )
        }

        // Projection flattens across every level, which is easy to misread as per-parent grouping.
        Text(
            text = "ⓘ aggregates across all matching elements at every level (paths flatten)",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Replaces one element of a list, returning a new list. */
internal fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }
