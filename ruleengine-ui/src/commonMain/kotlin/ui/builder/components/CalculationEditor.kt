package ui.builder.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
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
import ui.builder.BuilderOperand
import ui.builder.BuilderTerm
import ui.builder.CatalogFieldInfo
import ui.builder.OperandRules
import ui.builder.OperatorOptions
import ui.components.TinyButton

/**
 * Inline editor for an arithmetic operand, presented as a flat list of terms rather than an
 * expression tree: the first term stands alone and every later term carries the operator that joins
 * it to the running result.
 *
 * A term that is itself a calculation opens its own panel one level down, which is how
 * `(a + b) * c` is edited without drawing a tree.
 */
@Composable
fun CalculationEditor(
    calc: BuilderOperand.Calc,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand.Calc) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedTerm by remember { mutableStateOf<Int?>(value = null) }

    PanelCard(title = "Calculation", modifier = modifier) {
        calc.terms.forEachIndexed { index, term ->
            Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (index == 0) {
                        Text(
                            text = "     ",
                            style = MaterialTheme.typography.body2,
                            color = TextSecondary,
                        )
                    } else {
                        DropdownSelector(
                            selected = term.operator,
                            options = OperatorOptions.ARITHMETIC_OPERATORS,
                            onSelected = { operator ->
                                onChanged(calc.copy(terms = calc.terms.replaceAt(
                                    index = index,
                                    value = term.copy(operator = operator),
                                )))
                            },
                            modifier = Modifier.width(width = 70.dp),
                        )
                    }

                    OperandChip(
                        operand = term.operand,
                        // Terms are numeric by definition, so every operand kind stays available.
                        otherOperand = BuilderOperand.Literal(text = "0", numeric = true),
                        fields = fields,
                        expanded = expandedTerm == index,
                        onKindChanged = { replacement ->
                            onChanged(calc.copy(terms = calc.terms.replaceAt(
                                index = index,
                                value = term.copy(operand = replacement),
                            )))
                        },
                        onToggleExpanded = {
                            expandedTerm = if (expandedTerm == index) null else index
                        },
                    )

                    val literal = term.operand
                    if (literal is BuilderOperand.Literal) {
                        PlainTextField(
                            value = literal.text,
                            placeholder = "0",
                            onValueChange = { text ->
                                onChanged(calc.copy(terms = calc.terms.replaceAt(
                                    index = index,
                                    value = term.copy(
                                        operand = BuilderOperand.Literal(
                                            text = text,
                                            numeric = text.trim().toDoubleOrNull() != null,
                                        ),
                                    ),
                                )))
                            },
                            modifier = Modifier.width(width = 90.dp),
                        )
                    }

                    // A calculation needs at least two terms to mean anything.
                    if (calc.terms.size > 2) {
                        TinyButton(
                            text = "×",
                            onClick = {
                                onChanged(
                                    calc.copy(terms = calc.terms.filterIndexed { i, _ -> i != index })
                                )
                                expandedTerm = null
                            },
                        )
                    }
                }

                if (expandedTerm == index) {
                    NestedOperandEditor(
                        operand = term.operand,
                        fields = fields,
                        onChanged = { replacement ->
                            onChanged(calc.copy(terms = calc.terms.replaceAt(
                                index = index,
                                value = term.copy(operand = replacement),
                            )))
                        },
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TinyButton(
                text = "+ term",
                onClick = {
                    onChanged(
                        calc.copy(
                            terms = calc.terms + BuilderTerm(
                                operator = OperatorOptions.ARITHMETIC_OPERATORS.first(),
                                operand = BuilderOperand.Literal(text = "0", numeric = true),
                            )
                        )
                    )
                },
            )
            Checkbox(
                checked = calc.parenthesized,
                onCheckedChange = { onChanged(calc.copy(parenthesized = it)) },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
            )
            Text(
                text = "wrap in parentheses",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }

        Text(
            text = "ⓘ × ÷ bind before + −",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}

/**
 * Dispatches to the editor for a nested operand. Field and literal operands are edited on the row
 * itself, so only computed operands get a panel here.
 */
@Composable
fun NestedOperandEditor(
    operand: BuilderOperand,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (operand) {
        is BuilderOperand.Aggregate -> AggregateEditor(
            aggregate = operand,
            fields = fields,
            onChanged = onChanged,
            modifier = modifier,
        )

        is BuilderOperand.Calc -> CalculationEditor(
            calc = operand,
            fields = fields,
            onChanged = onChanged,
            modifier = modifier,
        )

        is BuilderOperand.FieldRef -> FieldPathEditor(
            fieldRef = operand,
            fields = fields,
            onChanged = onChanged,
            modifier = modifier,
        )

        is BuilderOperand.Literal -> Unit
    }
}

/** Inline editor for a field path, reusing the same N-segment breadcrumb as the aggregate panel. */
@Composable
fun FieldPathEditor(
    fieldRef: BuilderOperand.FieldRef,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand.FieldRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelCard(title = "Field", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            fieldRef.path.forEachIndexed { depth, step ->
                PathStepRow(
                    step = step,
                    depth = depth,
                    path = fieldRef.path,
                    fields = fields,
                    onStepChanged = { updated ->
                        onChanged(fieldRef.copy(path = fieldRef.path.replaceAt(index = depth, value = updated)))
                    },
                    onRemove = if (depth == 0) {
                        null
                    } else {
                        { onChanged(fieldRef.copy(path = fieldRef.path.take(n = depth))) }
                    },
                )
            }

            if (OperandRules.canAppendSegment(fields = fields, path = fieldRef.path)) {
                TinyButton(
                    text = "+ segment",
                    onClick = {
                        val next = OperandRules
                            .segmentOptions(fields = fields, path = fieldRef.path, depth = fieldRef.path.size)
                            .firstOrNull()?.id ?: ""
                        onChanged(fieldRef.copy(path = fieldRef.path + ui.builder.BuilderPathStep(name = next)))
                    },
                )
            }
        }
    }
}
