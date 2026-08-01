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
import ui.builder.OperandText
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

    TitledPanelCard(title = "Calculation", modifier = modifier) {
        calc.terms.forEachIndexed { index, term ->
            CalcTermRow(
                calc = calc,
                term = term,
                index = index,
                fields = fields,
                expanded = expandedTerm == index,
                onToggleExpanded = { expandedTerm = if (expandedTerm == index) null else index },
                onTermsChanged = { terms ->
                    if (terms.size < calc.terms.size) expandedTerm = null
                    onChanged(calc.copy(terms = terms))
                },
            )
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
 * One term of a calculation: its operator, its operand, and the expander for a nested operand.
 *
 * The first term has no operator — a calculation reads `a + b`, not `+ a + b` — so its slot is
 * blanked to keep the operands of every row aligned.
 */
@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
private fun CalcTermRow(
    calc: BuilderOperand.Calc,
    term: BuilderTerm,
    index: Int,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTermsChanged: (List<BuilderTerm>) -> Unit,
) {
    fun replaceTerm(value: BuilderTerm) = onTermsChanged(calc.terms.replaceAt(index = index, value = value))

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
                    onSelected = { operator -> replaceTerm(term.copy(operator = operator)) },
                    modifier = Modifier.width(width = 70.dp),
                )
            }

            OperandChip(
                operand = term.operand,
                // Terms are numeric by definition, so every operand kind stays available.
                otherOperand = BuilderOperand.Literal(text = "0", numeric = true),
                fields = fields,
                expanded = expanded,
                onKindChanged = { replacement -> replaceTerm(term.copy(operand = replacement)) },
                onToggleExpanded = onToggleExpanded,
            )

            val literal = term.operand
            if (literal is BuilderOperand.Literal) {
                PlainTextField(
                    value = literal.text,
                    placeholder = "0",
                    onValueChange = { text ->
                        replaceTerm(
                            term.copy(
                                operand = BuilderOperand.Literal(
                                    text = text,
                                    numeric = text.trim().toDoubleOrNull() != null,
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.width(width = 90.dp),
                )
            }

            // A calculation needs at least two terms to mean anything.
            if (calc.terms.size > 2) {
                TinyButton(
                    text = "×",
                    onClick = { onTermsChanged(calc.terms.filterIndexed { i, _ -> i != index }) },
                )
            }
        }

        if (expanded) {
            NestedOperandEditor(
                operand = term.operand,
                fields = fields,
                onChanged = { replacement -> replaceTerm(term.copy(operand = replacement)) },
                modifier = Modifier.padding(start = 24.dp),
            )
        }
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

/** Inline editor for a field path, reusing the same breadcrumb as the aggregate panel. */
@Composable
fun FieldPathEditor(
    fieldRef: BuilderOperand.FieldRef,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand.FieldRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    TitledPanelCard(
        title = "Field",
        detail = OperandText.toDsl(operand = fieldRef),
        modifier = modifier,
    ) {
        PathBreadcrumb(
            path = fieldRef.path,
            fields = fields,
            onPathChanged = { onChanged(fieldRef.copy(path = it)) },
        )
    }
}
