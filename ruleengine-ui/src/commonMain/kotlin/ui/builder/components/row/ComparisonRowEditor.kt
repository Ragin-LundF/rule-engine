package ui.builder.components.row

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.OperandRules
import ui.builder.OperandText
import ui.builder.components.IgnoreCaseToggle
import ui.builder.components.OperandChip
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.editor.NestedOperandEditor
import ui.builder.components.model.ExpandedSide
import ui.builder.model.BuilderOperand
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.MutableBuilderComparison
import ui.components.TinyButton

/**
 * A comparison row: two operand chips around a symbolic operator.
 *
 * Rows carrying a computed operand are marked with an accent stripe and a read-only DSL echo line, so
 * they stay distinguishable from — and as scannable as — plain condition rows.
 */
@Composable
fun ComparisonRowEditor(
    comparison: MutableBuilderComparison,
    fields: List<CatalogFieldInfo>,
    onSelected: () -> Unit,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(value = ExpandedSide.NONE) }
    val operators = OperandRules.operatorsFor(
        left = comparison.left,
        right = comparison.right,
        fields = fields,
    )

    Row(modifier = modifier.fillMaxWidth()) {
        // Accent stripe marking an advanced row.
        Box(
            modifier = Modifier
                .width(width = 3.dp)
                .fillMaxHeight()
                .background(color = PrimaryBlue.copy(alpha = 0.5f)),
        )

        Column(
            modifier = Modifier.padding(start = 8.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            ComparisonControls(
                comparison = comparison,
                fields = fields,
                operators = operators,
                expanded = expanded,
                onExpandedChange = { side -> expanded = side },
                onSelected = onSelected,
                onChanged = onChanged,
                onRemove = onRemove,
            )

            // Read-only echo of the generated DSL, so the row is always verifiable at a glance.
            Text(
                text = dslEcho(comparison = comparison),
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )

            when (expanded) {
                ExpandedSide.LEFT -> NestedOperandEditor(
                    operand = comparison.left,
                    fields = fields,
                    onChanged = {
                        comparison.left = it
                        onChanged()
                    },
                )

                ExpandedSide.RIGHT -> NestedOperandEditor(
                    operand = comparison.right,
                    fields = fields,
                    onChanged = {
                        comparison.right = it
                        onChanged()
                    },
                )

                ExpandedSide.NONE -> Unit
            }
        }
    }
}

/**
 * The row itself: drag handle, both operands, the operator between them, and the row's own controls.
 *
 * Only one side can be expanded at a time, so toggling a side that is already open closes it and
 * toggling the other simply moves the expansion across.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ComparisonControls(
    comparison: MutableBuilderComparison,
    fields: List<CatalogFieldInfo>,
    operators: List<String>,
    expanded: ExpandedSide,
    onExpandedChange: (ExpandedSide) -> Unit,
    onSelected: () -> Unit,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⠿",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
            modifier = Modifier.clickable(onClick = onSelected),
        )

        OperandSide(
            operand = comparison.left,
            other = comparison.right,
            fields = fields,
            expanded = expanded == ExpandedSide.LEFT,
            onOperandChanged = {
                comparison.left = it
                onChanged()
            },
            onToggleExpanded = {
                onExpandedChange(if (expanded == ExpandedSide.LEFT) ExpandedSide.NONE else ExpandedSide.LEFT)
            },
        )

        DropdownSelector(
            selected = comparison.operator,
            options = operators,
            onSelected = {
                comparison.operator = it
                onChanged()
            },
            modifier = Modifier.width(width = 80.dp),
        )

        OperandSide(
            operand = comparison.right,
            other = comparison.left,
            fields = fields,
            expanded = expanded == ExpandedSide.RIGHT,
            onOperandChanged = {
                comparison.right = it
                onChanged()
            },
            onToggleExpanded = {
                onExpandedChange(if (expanded == ExpandedSide.RIGHT) ExpandedSide.NONE else ExpandedSide.RIGHT)
            },
        )

        if (OperandRules.supportsIgnoreCase(left = comparison.left, right = comparison.right, fields = fields)) {
            IgnoreCaseToggle(
                checked = comparison.ignoreCase,
                onCheckedChange = {
                    comparison.ignoreCase = it
                    onChanged()
                },
            )
        }

        TinyButton(text = "×", onClick = onRemove)
    }
}

/**
 * One side of the comparison: the chip, plus an inline value box when the operand is a literal so a
 * simple number does not need an expand click.
 */
@Composable
private fun OperandSide(
    operand: BuilderOperand,
    other: BuilderOperand,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onOperandChanged: (BuilderOperand) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OperandChip(
            operand = operand,
            otherOperand = other,
            fields = fields,
            expanded = expanded,
            onKindChanged = onOperandChanged,
            onToggleExpanded = onToggleExpanded,
        )

        if (operand is BuilderOperand.Literal) {
            PlainTextField(
                value = operand.text,
                placeholder = "value",
                onValueChange = { text ->
                    onOperandChanged(
                        BuilderOperand.Literal(
                            text = text,
                            numeric = text.trim().toDoubleOrNull() != null,
                        )
                    )
                },
                modifier = Modifier.width(width = 120.dp),
            )
        }
    }
}

private fun dslEcho(comparison: MutableBuilderComparison): String {
    val not = if (comparison.negated) "not " else ""
    val ignoreCase = if (comparison.ignoreCase) " ignoreCase" else ""
    val left = OperandText.toDsl(operand = comparison.left)
    val right = OperandText.toDsl(operand = comparison.right)
    return "$not$left ${comparison.operator} $right$ignoreCase"
}
