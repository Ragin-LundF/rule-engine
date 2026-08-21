package ui.builder.components.row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.OperatorOptions
import ui.builder.components.dropdown.ActionDropdown
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.model.BuilderExtraction
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.scalarPaths
import ui.builder.model.mutable.MutableBuilderAction
import ui.components.TinyButton

/** The capture reference an extraction fills in, i.e. the `$1` in `extract … tag $1`. */
private const val EXTRACTION_ARGUMENT = "$1"

/**
 * A single editable action row: action dropdown, argument editor, and remove button — with the
 * optional `extract … regex(…)` clause on a line of its own above it.
 */
@Composable
fun ActionRowEditor(
    action: MutableBuilderAction,
    actions: List<CatalogActionInfo>,
    fields: List<CatalogFieldInfo>,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        val extraction = action.extraction
        if (extraction != null) {
            ExtractionRow(
                extraction = extraction,
                fields = fields,
                onChanged = { updated ->
                    action.extraction = updated
                    onChanged()
                },
                onRemove = {
                    action.extraction = null
                    // The argument referred to a capture group that no longer exists, so leaving `$1`
                    // there would generate a rule the compiler rejects.
                    if (action.arguments.firstOrNull()?.trim() == EXTRACTION_ARGUMENT) {
                        action.arguments[0] = ""
                    }
                    onChanged()
                },
            )
        }

        ActionCallRow(
            action = action,
            actions = actions,
            fields = fields,
            onChanged = onChanged,
            onRemove = onRemove,
        )
    }
}

/** The action itself: which action, its argument, and the row's own controls. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ActionCallRow(
    action: MutableBuilderAction,
    actions: List<CatalogActionInfo>,
    fields: List<CatalogFieldInfo>,
    onChanged: () -> Unit,
    onRemove: () -> Unit,
) {
    val argType = actions.firstOrNull { it.name == action.name }?.argType ?: "string"
    val takesArgument = argType != "none"
    // An extraction always produces a string, so it cannot fill an argument the action declares as a
    // variable reference — offering the button there would only build a rule the validator rejects.
    val takesExtraction = takesArgument && argType != VARIABLE_STRING_ARG && argType != VARIABLE_LIST_ARG

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionDropdown(
            selectedAction = action.name,
            actions = actions,
            onActionSelected = { selectedAction ->
                action.name = selectedAction.name
                action.arguments.clear()
                if (selectedAction.argType != "none") {
                    action.arguments.add("")
                }
                onChanged()
            },
            modifier = Modifier.width(width = 200.dp),
        )

        if (takesArgument) {
            ActionValueEditor(
                value = action.arguments.firstOrNull() ?: "",
                argType = argType,
                fields = fields,
                onValueChange = { newValue ->
                    if (action.arguments.isEmpty()) {
                        action.arguments.add(newValue)
                    } else {
                        action.arguments[0] = newValue
                    }
                    onChanged()
                },
                modifier = Modifier.weight(weight = 1f),
            )
        }

        // Offered only when the action takes an argument for the captured value to land in, and only
        // while it has no extraction — the clause above carries its own remove control.
        if (action.extraction == null && takesExtraction) {
            TinyButton(
                text = "⊕ extract",
                onClick = {
                    action.extraction = defaultExtraction(fields = fields)
                    action.arguments[0] = EXTRACTION_ARGUMENT
                    onChanged()
                },
            )
        }

        TinyButton(text = "×", onClick = onRemove)
    }
}

/**
 * The `extract <sourceField> regex("<pattern>", <group>)` clause.
 *
 * Reads left to right as the DSL does. The source field is picked from the schema's text fields
 * because `Validator` rejects an extraction over anything else, while the pattern and the group index
 * are typed — a regex has no useful set of options to choose from.
 */
@Composable
private fun ExtractionRow(
    extraction: BuilderExtraction,
    fields: List<CatalogFieldInfo>,
    onChanged: (BuilderExtraction) -> Unit,
    onRemove: () -> Unit,
) {
    val sourceOptions = textFieldOptions(fields = fields)

    Row(
        modifier = Modifier.padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text = "extract")

        DropdownSelector(
            selected = extraction.sourceField,
            options = sourceOptions.ifEmpty { listOf(extraction.sourceField) },
            onSelected = { onChanged(extraction.copy(sourceField = it)) },
            modifier = Modifier.width(width = 140.dp),
        )

        Label(text = "regex")

        PlainTextField(
            value = extraction.pattern,
            placeholder = "DE([0-9]+)",
            onValueChange = { onChanged(extraction.copy(pattern = it)) },
            modifier = Modifier.width(width = 180.dp),
        )

        Label(text = "group")

        PlainTextField(
            value = extraction.groupIndex.toString(),
            placeholder = "1",
            // A blank or non-numeric entry falls back to 0, the whole match — the one index that is
            // always valid, so a half-typed number cannot generate a rule that fails to compile.
            onValueChange = { text -> onChanged(extraction.copy(groupIndex = text.trim().toIntOrNull() ?: 0)) },
            modifier = Modifier.width(width = 48.dp),
        )

        TinyButton(text = "×", onClick = onRemove)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        color = TextSecondary,
    )
}

/** Text fields the extraction may read, by their dotted path. */
private fun textFieldOptions(fields: List<CatalogFieldInfo>): List<String> = fields.scalarPaths()
    .filter { field -> OperatorOptions.isTextType(fieldType = field.type) }
    .map { field -> field.id }

private fun defaultExtraction(fields: List<CatalogFieldInfo>): BuilderExtraction = BuilderExtraction(
    sourceField = textFieldOptions(fields = fields).firstOrNull() ?: "",
    pattern = "",
    // Group 1 rather than 0: a pattern is written with a capture group because the author wants the
    // group, and `(.*)` around the whole match is the rarer intent.
    groupIndex = 1,
)

/**
 * Typed value editor for an action argument.
 *
 * - `boolean` → dropdown with true/false options.
 * - `variable_string` / `variable_list` → dropdown of the variables that fit, because the argument is a
 *   reference to pick rather than a value to type. Typing one by hand into a text box is how you get a
 *   `$name` the validator then rejects.
 * - All other types → plain text field.
 */
@Composable
private fun ActionValueEditor(
    value: String,
    argType: String,
    fields: List<CatalogFieldInfo>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (argType == VARIABLE_STRING_ARG || argType == VARIABLE_LIST_ARG) {
        val options = variableOptions(fields = fields, wantList = argType == VARIABLE_LIST_ARG)
        VariableArgumentEditor(
            value = value,
            options = options,
            onValueChange = onValueChange,
            modifier = modifier,
        )
        return
    }
    if (argType == "boolean") {
        DropdownSelector(
            selected = value.ifBlank { "true" },
            options = listOf("true", "false"),
            onSelected = onValueChange,
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = modifier.defaultMinSize(minWidth = 120.dp),
        )
    }
}

/**
 * The variable picker, or a text box when the rule set has no variable of the required kind yet.
 *
 * A dropdown with nothing in it cannot be filled, and an author writing the action before the `set`
 * that feeds it is the normal order of work — so the text box stays available as the way out.
 */
@Composable
private fun VariableArgumentEditor(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = modifier.defaultMinSize(minWidth = 120.dp),
        )
        return
    }
    DropdownSelector(
        selected = value.ifBlank { options.first() },
        options = options,
        onSelected = onValueChange,
        modifier = modifier,
    )
}

/**
 * The `$name` references of the requested kind.
 *
 * A list variable is the one the catalog types as such — it is written with `add`, which is exactly the
 * clause `variable_list` declares. Everything else with a `$` id was written with `set`.
 */
private fun variableOptions(fields: List<CatalogFieldInfo>, wantList: Boolean): List<String> {
    return fields
        .filter { field -> OperatorOptions.isVariableId(fieldId = field.id) }
        .filter { field -> OperatorOptions.isListVariableType(fieldType = field.type) == wantList }
        .map { field -> field.id }
}

/** The lowercased `ActionArgType` names the Builder catalog carries for a variable argument. */
private const val VARIABLE_STRING_ARG: String = "variable_string"
private const val VARIABLE_LIST_ARG: String = "variable_list"
