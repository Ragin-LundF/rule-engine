package ui.builder.components.row

import androidx.compose.foundation.background
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
import ruleengine.dsl.ast.AssignmentKindAst
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.OperandText
import ui.builder.components.OperandChip
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.editor.NestedOperandEditor
import ui.builder.model.BuilderOperand
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.MutableBuilderVariable
import ui.components.TinyButton

/**
 * An assignment row: `set <name> = <operand>` or `add <operand> to <name>`.
 *
 * The value is an ordinary operand chip, so an assignment can hold a field, a literal, an aggregate
 * or a calculation without any editor of its own. Like a comparison row it carries an accent stripe
 * and a DSL echo, because it is an advanced construct whose generated text is worth seeing.
 *
 * The two kinds put their parts in different places, because each row reads as the clause it will
 * generate: a `set` names its target first, an `add` names its value first.
 */
@Composable
fun VariableRowEditor(
    variable: MutableBuilderVariable,
    fields: List<CatalogFieldInfo>,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Row(modifier = modifier.fillMaxWidth()) {
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
            VariableControls(
                variable = variable,
                fields = fields,
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
                onChanged = onChanged,
                onRemove = onRemove,
            )

            Text(
                text = dslEcho(variable = variable),
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )

            if (expanded) {
                NestedOperandEditor(
                    operand = variable.expression,
                    fields = fields,
                    onChanged = { operand ->
                        variable.expression = operand
                        onChanged()
                    },
                )
            }
        }
    }
}

/**
 * The row itself: the kind selector, then the name and value in the order the chosen clause writes
 * them, then the remove button.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun VariableControls(
    variable: MutableBuilderVariable,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownSelector(
            selected = keywordOf(kind = variable.kind),
            options = KEYWORDS,
            onSelected = { keyword ->
                variable.kind = kindOf(keyword = keyword)
                onChanged()
            },
            modifier = Modifier.width(width = 76.dp),
            placeholder = "set",
        )

        if (variable.kind == AssignmentKindAst.SET) {
            VariableNameField(variable = variable, onChanged = onChanged)
            Text(text = "=", style = MaterialTheme.typography.body2, color = TextSecondary)
            VariableValue(
                variable = variable,
                fields = fields,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onChanged = onChanged,
            )
        } else {
            VariableValue(
                variable = variable,
                fields = fields,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
                onChanged = onChanged,
            )
            Text(text = "to", style = MaterialTheme.typography.body2, color = TextSecondary)
            VariableNameField(variable = variable, onChanged = onChanged)
        }

        TinyButton(text = "×", onClick = onRemove)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun VariableNameField(variable: MutableBuilderVariable, onChanged: () -> Unit) {
    PlainTextField(
        value = variable.name,
        placeholder = "name",
        onValueChange = { text ->
            variable.name = text.trim()
            onChanged()
        },
        modifier = Modifier.width(width = 140.dp),
    )
}

/** The operand chip, plus an inline box when the operand is a literal the author types directly. */
@Suppress("FunctionNaming")
@Composable
private fun VariableValue(
    variable: MutableBuilderVariable,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onChanged: () -> Unit,
) {
    OperandChip(
        operand = variable.expression,
        // No second side to constrain the kinds: an assignment may hold any of them.
        otherOperand = UNCONSTRAINED_OPERAND,
        fields = fields,
        expanded = expanded,
        onKindChanged = { operand ->
            variable.expression = operand
            onChanged()
        },
        onToggleExpanded = onToggleExpanded,
    )

    val literal = variable.expression as? BuilderOperand.Literal
    if (literal != null) {
        PlainTextField(
            value = literal.text,
            placeholder = "value",
            onValueChange = { text ->
                variable.expression = BuilderOperand.Literal(
                    text = text,
                    numeric = text.trim().toDoubleOrNull() != null,
                )
                onChanged()
            },
            modifier = Modifier.width(width = 120.dp),
        )
    }
}

private const val SET_KEYWORD = "set"
private const val ADD_KEYWORD = "add"

private val KEYWORDS = listOf(SET_KEYWORD, ADD_KEYWORD)

private fun keywordOf(kind: AssignmentKindAst): String =
    if (kind == AssignmentKindAst.ADD) ADD_KEYWORD else SET_KEYWORD

private fun kindOf(keyword: String): AssignmentKindAst =
    if (keyword == ADD_KEYWORD) AssignmentKindAst.ADD else AssignmentKindAst.SET

/**
 * Stands in for the "other side" an operand chip expects. A blank literal counts as possibly
 * numeric, which is what makes every operand kind selectable.
 */
private val UNCONSTRAINED_OPERAND = BuilderOperand.Literal(text = "", numeric = false)

private fun dslEcho(variable: MutableBuilderVariable): String {
    val name = variable.name.ifBlank { "…" }
    val value = OperandText.toDsl(operand = variable.expression)
    return when (variable.kind) {
        AssignmentKindAst.SET -> "set $name = $value"
        AssignmentKindAst.ADD -> "add $value to $name"
    }
}
