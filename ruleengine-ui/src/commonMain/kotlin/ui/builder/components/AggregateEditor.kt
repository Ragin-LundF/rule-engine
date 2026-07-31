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
import ui.TextSecondary
import ui.builder.BuilderOperand
import ui.builder.BuilderPathStep
import ui.builder.CatalogFieldInfo
import ui.builder.OperandRules
import ui.builder.OperatorOptions
import ui.components.TinyButton

/**
 * Inline editor for an aggregate operand: the function, the path it aggregates over, and the filters
 * on each path segment.
 *
 * The path is an N-segment breadcrumb — segments are rendered in a loop by [PathStepRow], so depth is
 * unbounded and a deep path needs no extra code here.
 */
@Composable
fun AggregateEditor(
    aggregate: BuilderOperand.Aggregate,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand.Aggregate) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelCard(title = "Aggregate", modifier = modifier) {
        LabelledRow(label = "function") {
            DropdownSelector(
                selected = aggregate.function,
                options = OperatorOptions.AGGREGATE_FUNCTIONS,
                onSelected = { onChanged(aggregate.copy(function = it)) },
                modifier = Modifier.width(width = 130.dp),
            )
        }

        LabelledRow(label = "over") {
            Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                aggregate.path.forEachIndexed { depth, step ->
                    PathStepRow(
                        step = step,
                        depth = depth,
                        path = aggregate.path,
                        fields = fields,
                        onStepChanged = { updated ->
                            onChanged(aggregate.copy(path = aggregate.path.replaceAt(index = depth, value = updated)))
                        },
                        // The first segment is the collection itself and cannot be dropped.
                        onRemove = if (depth == 0) {
                            null
                        } else {
                            { onChanged(aggregate.copy(path = aggregate.path.take(n = depth))) }
                        },
                    )
                }

                if (OperandRules.canAppendSegment(fields = fields, path = aggregate.path)) {
                    TinyButton(
                        text = "+ segment",
                        onClick = {
                            val next = OperandRules
                                .segmentOptions(fields = fields, path = aggregate.path, depth = aggregate.path.size)
                                .firstOrNull()?.id ?: ""
                            onChanged(aggregate.copy(path = aggregate.path + BuilderPathStep(name = next)))
                        },
                    )
                }
            }
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

/** A bordered panel used by the inline operand editors. */
@Composable
internal fun PanelCard(
    title: String,
    modifier: Modifier = Modifier,
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
        Text(
            text = title,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
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
