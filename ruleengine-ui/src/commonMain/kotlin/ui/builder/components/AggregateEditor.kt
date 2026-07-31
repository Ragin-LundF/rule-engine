package ui.builder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.BorderColor
import ui.TextMuted
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
    PanelCard(
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

/**
 * A bordered panel used by the inline operand editors.
 *
 * [detail] echoes the DSL the panel currently generates, so what the controls produce is verifiable
 * without switching to Code mode.
 */
@Composable
internal fun PanelCard(
    title: String,
    modifier: Modifier = Modifier,
    detail: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.caption,
                    color = TextMuted,
                )
            }
        }
        content()
    }
}

/** A label column plus content, so the panels line up. */
@Composable
internal fun LabelledRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.width(width = 64.dp).padding(top = 10.dp),
        )
        content()
    }
}
