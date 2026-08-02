package ui.builder.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import ruleengine.evaluator.compiled.DslFunctions
import ui.TextSecondary
import ui.builder.OperandText
import ui.builder.components.OperandChip
import ui.builder.components.TitledPanelCard
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderOperand
import ui.builder.model.catalog.CatalogFieldInfo
import ui.components.TinyButton

/**
 * Inline editor for a function call: the function, and one row per argument.
 *
 * Deliberately generic rather than one panel per function. The DSL's calls differ only in how many
 * arguments they take and what those mean, and an argument is an ordinary operand — so the same
 * chip-and-nested-editor pattern the calculation panel uses covers `abs(sum(a) - sum(b))`,
 * `daysBetween(from, to)` and `sumByKey("month", sales.amount, refunds.amount)` alike.
 *
 * Arity is not enforced here. The engine's validator reports it against the rule text with a message
 * naming the function, and blocking the edit mid-way would stop an author from adding the second
 * argument after the first.
 */
@Composable
fun FunctionCallEditor(
    call: BuilderOperand.Call,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderOperand.Call) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedArg by remember { mutableStateOf<Int?>(value = null) }

    TitledPanelCard(
        title = "Function",
        detail = OperandText.toDsl(operand = call),
        modifier = modifier,
    ) {
        DropdownSelector(
            selected = call.function,
            options = FUNCTION_OPTIONS,
            onSelected = { function -> onChanged(call.copy(function = function)) },
            modifier = Modifier.width(width = 150.dp),
        )

        call.args.forEachIndexed { index, arg ->
            CallArgumentRow(
                call = call,
                arg = arg,
                index = index,
                fields = fields,
                expanded = expandedArg == index,
                onToggleExpanded = { expandedArg = if (expandedArg == index) null else index },
                onArgsChanged = { args ->
                    if (args.size < call.args.size) expandedArg = null
                    onChanged(call.copy(args = args))
                },
            )
        }

        TinyButton(
            text = "+ argument",
            onClick = {
                onChanged(call.copy(args = call.args + BuilderOperand.Literal(text = "", numeric = false)))
            },
        )
    }
}

/**
 * The functions offered here.
 *
 * `take` and `takeLast` are absent on purpose: they narrow a path rather than compute a value, and
 * the breadcrumb's own slice control is where they are edited.
 */
private val FUNCTION_OPTIONS: List<String> =
    DslFunctions.allNames().filterNot { name -> name in DslFunctions.SLICE_NAMES }

/** One argument: its operand chip, the inline literal editor, and the expander for a nested panel. */
@Suppress("LongParameterList")
@Composable
private fun CallArgumentRow(
    call: BuilderOperand.Call,
    arg: BuilderOperand,
    index: Int,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onArgsChanged: (List<BuilderOperand>) -> Unit,
) {
    fun replaceArg(value: BuilderOperand) = onArgsChanged(call.args.replaceAt(index = index, value = value))

    Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )

            OperandChip(
                operand = arg,
                // Every kind stays available: which argument types a function accepts is the
                // validator's call, and it reports it against the rule text.
                otherOperand = BuilderOperand.Literal(text = "0", numeric = true),
                fields = fields,
                expanded = expanded,
                onKindChanged = { replacement -> replaceArg(replacement) },
                onToggleExpanded = onToggleExpanded,
            )

            val literal = arg
            if (literal is BuilderOperand.Literal) {
                PlainTextField(
                    value = literal.text,
                    placeholder = "value",
                    onValueChange = { text ->
                        replaceArg(
                            BuilderOperand.Literal(
                                text = text,
                                numeric = text.trim().toDoubleOrNull() != null,
                            )
                        )
                    },
                    modifier = Modifier.width(width = 130.dp),
                )
            }

            if (call.args.size > 1) {
                TinyButton(
                    text = "×",
                    onClick = { onArgsChanged(call.args.filterIndexed { i, _ -> i != index }) },
                )
            }
        }

        if (expanded) {
            NestedOperandEditor(
                operand = arg,
                fields = fields,
                onChanged = { replacement -> replaceArg(replacement) },
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}
