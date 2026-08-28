package ui.builder.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruleengine.evaluator.compiled.DslFunctions
import ui.builder.OperandRules
import ui.builder.OperandText
import ui.builder.OperatorOptions
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderTerm
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.selection.SelectionStep
import ui.components.TinyButton
import ui.util.replaceAt

/**
 * The editor for one operand, whatever kind it is.
 *
 * Reached by drilling from a row, and it renders **one level**: an aggregate shows its reduction and
 * its path; a calculation shows its terms as cards that drill further. That is what makes depth
 * navigation rather than layout — nothing here expands, so the row this belongs to never moves.
 *
 * [write] is the setter the selection walk handed back. It rebuilds every immutable value between this
 * operand and the row's own Compose state slot, so an edit five levels down is one assignment.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun OperandEditor(
    operand: BuilderOperand,
    fields: BuilderCatalog,
    write: (BuilderOperand) -> Unit,
    onEdited: () -> Unit,
    onDrill: (SelectionStep) -> Unit,
    onDrillFilter: (depth: Int, filterIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun replace(value: BuilderOperand) {
        write(value)
        onEdited()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        DslEcho(text = OperandText.toDsl(operand = operand))

        InspectorField(label = "This operand is") {
            OperandKindPicker(
                current = OperandRules.kindOf(operand = operand),
                onSelect = { kind ->
                    replace(
                        OperandRules.defaultOperand(kind = kind, fields = fields, previous = operand),
                    )
                },
            )
        }

        when (operand) {
            is BuilderOperand.FieldRef -> PathSteps(
                path = operand.path,
                fields = fields,
                onPathChanged = { path -> replace(operand.copy(path = path)) },
                onDrillFilter = onDrillFilter,
            )

            is BuilderOperand.Literal -> LiteralEditor(
                literal = operand,
                onChanged = { value -> replace(value) },
            )

            is BuilderOperand.ListLiteral -> ListEditor(
                list = operand,
                onChanged = { value -> replace(value) },
            )

            is BuilderOperand.Aggregate -> AggregateFields(
                aggregate = operand,
                fields = fields,
                onChanged = { value -> replace(value) },
                onDrillFilter = onDrillFilter,
            )

            is BuilderOperand.Calc -> CalculationFields(
                calc = operand,
                onChanged = { value -> replace(value) },
                onDrill = onDrill,
            )

            is BuilderOperand.Call -> CallFields(
                call = operand,
                onChanged = { value -> replace(value) },
                onDrill = onDrill,
            )
        }
    }
}

/** A typed literal. The quoting rule is the same one a condition's value follows. */
@Suppress("FunctionNaming")
@Composable
private fun LiteralEditor(
    literal: BuilderOperand.Literal,
    onChanged: (BuilderOperand) -> Unit,
) {
    InspectorField(label = "Value") {
        PlainTextField(
            value = literal.text,
            placeholder = "value",
            onValueChange = { text ->
                onChanged(
                    BuilderOperand.Literal(
                        text = text,
                        numeric = text.trim().toDoubleOrNull() != null,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    InspectorNote(
        text = if (literal.numeric) {
            "Reads as a number, so it is written unquoted."
        } else {
            "Written quoted. A value that reads as a number is written unquoted instead."
        },
    )
}

/** A written-out list — only ever the right side of a membership test. */
@Suppress("FunctionNaming")
@Composable
private fun ListEditor(
    list: BuilderOperand.ListLiteral,
    onChanged: (BuilderOperand) -> Unit,
) {
    InspectorField(label = "Values", hint = "${list.items.size} items") {
        Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            list.items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
                ) {
                    PlainTextField(
                        value = item,
                        placeholder = "value",
                        onValueChange = { text ->
                            onChanged(list.copy(items = list.items.replaceAt(index = index, value = text)))
                        },
                        modifier = Modifier.weight(weight = 1f),
                    )
                    TinyButton(
                        text = "×",
                        onClick = {
                            onChanged(list.copy(items = list.items.filterIndexed { i, _ -> i != index }))
                        },
                    )
                }
            }
            TinyButton(
                text = "+ value",
                onClick = { onChanged(list.copy(items = list.items + "")) },
            )
        }
    }
    InspectorNote(
        text = "For membership against another field, switch this side to Field and name it — a bare " +
            "name stays a membership test rather than becoming text.",
    )
}

/** An aggregate: which reduction, and over which path. */
@Suppress("FunctionNaming")
@Composable
private fun AggregateFields(
    aggregate: BuilderOperand.Aggregate,
    fields: BuilderCatalog,
    onChanged: (BuilderOperand) -> Unit,
    onDrillFilter: (depth: Int, filterIndex: Int) -> Unit,
) {
    InspectorField(label = "Reduction") {
        InspectorOptions(
            options = OperatorOptions.AGGREGATE_FUNCTIONS,
            selected = aggregate.function,
            onSelect = { selected -> onChanged(aggregate.copy(function = selected)) },
            hints = AGGREGATE_HINTS,
        )
    }
    PathSteps(
        path = aggregate.path,
        fields = fields,
        onPathChanged = { path -> onChanged(aggregate.copy(path = path)) },
        onDrillFilter = onDrillFilter,
    )
    InspectorNote(
        text = "Aggregates flatten across every level of the path: this reduces all matching leaves, " +
            "not one total per parent.",
    )
}

/** What each reduction means, so the list teaches rather than just lists. */
private val AGGREGATE_HINTS: Map<String, String> = mapOf(
    "count" to "how many",
    "sum" to "total",
    "avg" to "mean",
    "median" to "middle value",
    "max" to "largest",
    "min" to "smallest",
    "subtract" to "first minus the rest",
)

/** An arithmetic chain, as a flat list of terms. */
@Suppress("FunctionNaming")
@Composable
private fun CalculationFields(
    calc: BuilderOperand.Calc,
    onChanged: (BuilderOperand) -> Unit,
    onDrill: (SelectionStep) -> Unit,
) {
    InspectorSection(title = "Terms", hint = "× ÷ bind before + −")
    calc.terms.forEachIndexed { index, term ->
        CalcTermRow(
            calc = calc,
            term = term,
            index = index,
            onChanged = onChanged,
            onDrill = onDrill,
        )
    }
    InspectorActions {
        TinyButton(
            text = "+ term",
            onClick = {
                onChanged(
                    calc.copy(
                        terms = calc.terms + BuilderTerm(
                            operator = OperatorOptions.ARITHMETIC_OPERATORS.first(),
                            operand = BuilderOperand.Literal(text = "1", numeric = true),
                        ),
                    ),
                )
            },
        )
    }
    InspectorToggle(
        label = "wrap in parentheses",
        checked = calc.parenthesized,
        onCheckedChange = { value -> onChanged(calc.copy(parenthesized = value)) },
        hint = "needed when this calculation is a term of another one",
    )
}

/**
 * One term: the operator that joins it, the operand card, and a remove where one is allowed.
 *
 * The first term carries no operator — a calculation reads `a + b`, not `+ a + b` — so its slot is
 * blanked to keep the cards of every row aligned.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun CalcTermRow(
    calc: BuilderOperand.Calc,
    term: BuilderTerm,
    index: Int,
    onChanged: (BuilderOperand) -> Unit,
    onDrill: (SelectionStep) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        if (index == 0) {
            Row(modifier = Modifier.width(width = 64.dp)) {}
        } else {
            DropdownSelector(
                selected = term.operator,
                options = OperatorOptions.ARITHMETIC_OPERATORS,
                onSelected = { selected ->
                    onChanged(
                        calc.copy(
                            terms = calc.terms.replaceAt(
                                index = index,
                                value = term.copy(operator = selected),
                            ),
                        ),
                    )
                },
                modifier = Modifier.width(width = 64.dp),
            )
        }
        Column(modifier = Modifier.weight(weight = 1f)) {
            OperandCard(
                operand = term.operand,
                onDrill = { onDrill(SelectionStep.Term(index = index)) },
            )
        }
        // A calculation needs two terms to mean anything.
        if (calc.terms.size > 2) {
            TinyButton(
                text = "×",
                onClick = {
                    onChanged(calc.copy(terms = calc.terms.filterIndexed { i, _ -> i != index }))
                },
            )
        }
    }
}

/** Any other call: the function, and one card per argument. */
@Suppress("FunctionNaming")
@Composable
private fun CallFields(
    call: BuilderOperand.Call,
    onChanged: (BuilderOperand) -> Unit,
    onDrill: (SelectionStep) -> Unit,
) {
    InspectorField(label = "Function") {
        InspectorOptions(
            options = FUNCTION_OPTIONS,
            selected = call.function,
            onSelect = { selected -> onChanged(call.copy(function = selected)) },
        )
    }
    InspectorNote(
        text = "take, takeLast and sortBy are absent on purpose: they rearrange a path rather than " +
            "compute a value, and the path steps below own them.",
    )
    InspectorSection(title = "Arguments")
    call.args.forEachIndexed { index, arg ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            Column(modifier = Modifier.weight(weight = 1f)) {
                OperandCard(
                    operand = arg,
                    onDrill = { onDrill(SelectionStep.Argument(index = index)) },
                )
            }
            if (call.args.size > 1) {
                TinyButton(
                    text = "×",
                    onClick = {
                        onChanged(call.copy(args = call.args.filterIndexed { i, _ -> i != index }))
                    },
                )
            }
        }
    }
    InspectorActions {
        TinyButton(
            text = "+ argument",
            onClick = {
                onChanged(
                    call.copy(args = call.args + BuilderOperand.Literal(text = "0", numeric = true)),
                )
            },
        )
    }
    InspectorNote(
        text = "Arity is the validator's call, reported against the rule text — so a half-finished " +
            "call is never blocked mid-edit.",
    )
}

/**
 * The functions offered here.
 *
 * The path-shaping calls are excluded: offering one would produce text the parser reads as a path
 * segment rather than as the call this edits.
 */
private val FUNCTION_OPTIONS: List<String> =
    DslFunctions.allNames().filterNot { name -> name in DslFunctions.pathFunctionNames() }
