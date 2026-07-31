package ui.builder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.BuilderFilter
import ui.builder.BuilderPathStep
import ui.builder.CatalogFieldInfo
import ui.builder.OperandRules
import ui.builder.OperatorOptions
import ui.components.TinyButton

/**
 * One segment of a field path, with the filters attached to it.
 *
 * Rendered once per segment in a loop, so a two-segment and a six-segment path share this code. The
 * segment dropdown is populated from the schema node the preceding segments point at, which is what
 * lets the picker descend to any declared depth.
 */
@Composable
fun PathStepRow(
    step: BuilderPathStep,
    depth: Int,
    path: List<BuilderPathStep>,
    fields: List<CatalogFieldInfo>,
    onStepChanged: (BuilderPathStep) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val options = OperandRules.segmentOptions(fields = fields, path = path, depth = depth)
    val filterOptions = OperandRules.filterFieldOptions(fields = fields, path = path, depth = depth)

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (depth == 0) " " else "▸",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )

            if (options.isEmpty()) {
                // Undeclared structure: no members to offer, so the name is typed directly.
                PlainTextField(
                    value = step.name,
                    placeholder = "field",
                    onValueChange = { onStepChanged(step.copy(name = it)) },
                    modifier = Modifier.width(width = 150.dp),
                )
            } else {
                DropdownSelector(
                    selected = step.name,
                    options = options.map { it.id },
                    onSelected = { selected ->
                        // Changing a segment invalidates filters that referenced the old element.
                        onStepChanged(BuilderPathStep(name = selected))
                    },
                    modifier = Modifier.width(width = 150.dp),
                )
            }

            if (filterOptions.isNotEmpty() || step.filters.isNotEmpty()) {
                TinyButton(
                    text = "⊕ where",
                    onClick = {
                        val defaultField = filterOptions.firstOrNull()?.id ?: ""
                        onStepChanged(
                            step.copy(
                                filters = step.filters + BuilderFilter(
                                    field = defaultField,
                                    operator = OperatorOptions.FILTER_OPERATORS.first(),
                                    value = "",
                                )
                            )
                        )
                    },
                )
            }

            if (onRemove != null) {
                TinyButton(text = "×", onClick = onRemove)
            }
        }

        step.filters.forEachIndexed { filterIndex, filter ->
            FilterConditionRow(
                filter = filter,
                fieldOptions = filterOptions,
                onFilterChanged = { updated ->
                    onStepChanged(
                        step.copy(
                            filters = step.filters.toMutableList().also { it[filterIndex] = updated }
                        )
                    )
                },
                onRemove = {
                    onStepChanged(
                        step.copy(filters = step.filters.filterIndexed { i, _ -> i != filterIndex })
                    )
                },
                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
            )
        }
    }
}
