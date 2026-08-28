package ui.builder.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ruleengine.dsl.ast.AssignmentKindAst
import ui.builder.OperandRules
import ui.builder.OperandText
import ui.builder.OperatorOptions
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderExtraction
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.scalarPaths
import ui.builder.model.mutable.MutableBuilderAction
import ui.builder.model.mutable.MutableBuilderVariable
import ui.builder.model.selection.SelectionStep
import ui.components.TinyButton

/** The capture reference an extraction fills in — the `$1` in `extract … label $1`. */
private const val EXTRACTION_ARGUMENT = "$1"

/** Action argument types that name a variable to pick rather than a value to type. */
private const val VARIABLE_STRING_ARG = "variable_string"
private const val VARIABLE_LIST_ARG = "variable_list"

/**
 * The editor for an action row: which action, its argument typed the way the schema declares it, and
 * the optional `extract` clause that computes the argument from the record.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ActionEditor(
    action: MutableBuilderAction,
    actions: List<CatalogActionInfo>,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
    onDrill: (SelectionStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val declared = actions.firstOrNull { it.name == action.name }
    val argType = declared?.argType ?: "string"

    Column(modifier = modifier.fillMaxWidth()) {
        DslEcho(text = actionEcho(action = action))

        InspectorField(label = "Action", hint = argType) {
            DropdownSelector(
                selected = action.name,
                options = actions.map { candidate -> candidate.name },
                onSelected = { selected ->
                    action.name = selected
                    action.arguments.clear()
                    val selectedType = actions.firstOrNull { it.name == selected }?.argType
                    if (selectedType != null && selectedType != "none") {
                        action.arguments.add("")
                    }
                    onEdited()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ActionArgument(
            action = action,
            argType = argType,
            fields = fields,
            onEdited = onEdited,
        )

        ExtractionSection(
            action = action,
            argType = argType,
            fields = fields,
            onEdited = onEdited,
            onDrill = onDrill,
        )
    }
}

/** The argument editor, chosen by the type the action schema declares. */
@Suppress("FunctionNaming")
@Composable
private fun ActionArgument(
    action: MutableBuilderAction,
    argType: String,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
) {
    if (argType == "none") {
        InspectorNote(text = "This action declares no argument.")
        return
    }
    val current = action.arguments.firstOrNull().orEmpty()

    fun write(value: String) {
        if (action.arguments.isEmpty()) {
            action.arguments.add(value)
        } else {
            action.arguments[0] = value
        }
        onEdited()
    }

    when {
        argType == "boolean" -> InspectorField(label = "Argument") {
            InspectorOptions(
                options = listOf("true", "false"),
                selected = current.ifBlank { "true" },
                onSelect = { selected -> write(selected) },
            )
        }

        argType == VARIABLE_STRING_ARG || argType == VARIABLE_LIST_ARG -> {
            val options = variableOptions(fields = fields, wantList = argType == VARIABLE_LIST_ARG)
            InspectorField(label = "Argument", hint = argType.replace(oldValue = "_", newValue = " ")) {
                if (options.isEmpty()) {
                    // An author writing the action before the `set` that feeds it is the normal order of
                    // work, so the text box stays available rather than an empty dropdown.
                    PlainTextField(
                        value = current,
                        placeholder = "\$name",
                        onValueChange = { value -> write(value) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DropdownSelector(
                        selected = current.ifBlank { options.first() },
                        options = options,
                        onSelected = { selected -> write(selected) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            InspectorNote(
                text = "A reference to pick, not a value to type: a hand-typed \$name is how you get " +
                    "one the validator rejects.",
            )
        }

        else -> InspectorField(label = "Argument", hint = argType) {
            PlainTextField(
                value = current,
                placeholder = "value",
                onValueChange = { value -> write(value) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The `extract <field> regex("<pattern>", <group>)` clause, added or removed as a whole. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ExtractionSection(
    action: MutableBuilderAction,
    argType: String,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
    onDrill: (SelectionStep) -> Unit,
) {
    InspectorSection(title = "extract clause")
    val extraction = action.extraction
    // An extraction always produces a string, so it cannot fill an argument declared as a variable
    // reference — offering it there would only build a rule the validator rejects.
    val eligible = argType != "none" && argType != VARIABLE_STRING_ARG && argType != VARIABLE_LIST_ARG

    when {
        extraction != null -> {
            InspectorNote(
                text = "Computes the argument from the record instead of taking a literal, and fills " +
                    "$EXTRACTION_ARGUMENT.",
            )
            InspectorLine(
                text = extractionEcho(extraction = extraction),
                selected = false,
                onClick = { onDrill(SelectionStep.Extraction) },
            )
            InspectorActions {
                TinyButton(
                    text = "Remove extract",
                    onClick = {
                        action.extraction = null
                        // The argument referred to a capture group that no longer exists.
                        if (action.arguments.firstOrNull()?.trim() == EXTRACTION_ARGUMENT) {
                            action.arguments[0] = ""
                        }
                        onEdited()
                    },
                )
            }
        }

        eligible -> InspectorActions {
            TinyButton(
                text = "+ extract from a text field",
                onClick = {
                    action.extraction = defaultExtraction(fields = fields)
                    if (action.arguments.isEmpty()) {
                        action.arguments.add(EXTRACTION_ARGUMENT)
                    } else {
                        action.arguments[0] = EXTRACTION_ARGUMENT
                    }
                    onEdited()
                },
            )
        }

        else -> InspectorNote(
            text = "Not available here: an extraction produces a string, which cannot fill a $argType " +
                "argument.",
        )
    }
}

/** The extraction itself: source field, pattern, capture group. */
@Suppress("FunctionNaming")
@Composable
internal fun ExtractionEditor(
    extraction: BuilderExtraction,
    fields: BuilderCatalog,
    write: (BuilderExtraction) -> Unit,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun update(value: BuilderExtraction) {
        write(value)
        onEdited()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        DslEcho(text = extractionEcho(extraction = extraction))
        InspectorField(label = "Source field", hint = "text only") {
            DropdownSelector(
                selected = extraction.sourceField,
                options = textFieldOptions(fields = fields).ifEmpty { listOf(extraction.sourceField) },
                onSelected = { selected -> update(extraction.copy(sourceField = selected)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InspectorField(label = "Pattern") {
            PlainTextField(
                value = extraction.pattern,
                placeholder = "DE([0-9]+)",
                onValueChange = { value -> update(extraction.copy(pattern = value)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InspectorField(label = "Capture group") {
            PlainTextField(
                value = extraction.groupIndex.toString(),
                placeholder = "1",
                // A blank or non-numeric entry falls back to 0, the whole match — the one index that is
                // always valid, so a half-typed number cannot generate a rule that fails to compile.
                onValueChange = { value ->
                    update(extraction.copy(groupIndex = value.trim().toIntOrNull() ?: 0))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InspectorNote(
            text = "The engine rejects an extraction over anything but a text field, which is why the " +
                "source list holds only those.",
        )
    }
}

/**
 * The editor for a `set` or `add` row.
 *
 * The two kinds put their parts in different places in the DSL — a `set` names its target first, an
 * `add` names its value first — but they hold the same three things, so one editor covers both.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun AssignmentEditor(
    assignment: MutableBuilderVariable,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
    onDrill: (SelectionStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DslEcho(text = assignmentEcho(assignment = assignment))

        InspectorField(label = "Clause") {
            InspectorOptions(
                options = listOf("set", "add"),
                selected = if (assignment.kind == AssignmentKindAst.ADD) "add" else "set",
                onSelect = { selected ->
                    assignment.kind = if (selected == "add") {
                        AssignmentKindAst.ADD
                    } else {
                        AssignmentKindAst.SET
                    }
                    onEdited()
                },
                hints = mapOf("set" to "holds one value", "add" to "appends to a list"),
            )
        }

        InspectorField(label = "Name") {
            PlainTextField(
                value = assignment.name,
                placeholder = "name",
                onValueChange = { value ->
                    assignment.name = value.trim()
                    onEdited()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InspectorNote(
            text = "Rules after this one — in manifest order — read it as \$${assignment.name}. It must " +
                "not be named like a schema field.",
        )

        InspectorSection(title = "Value")
        OperandKindPicker(
            current = OperandRules.kindOf(operand = assignment.expression),
            onSelect = { kind ->
                assignment.expression = OperandRules.defaultOperand(
                    kind = kind,
                    fields = fields,
                    previous = assignment.expression,
                )
                onEdited()
                onDrill(SelectionStep.Value)
            },
        )
        OperandCard(
            operand = assignment.expression,
            onDrill = { onDrill(SelectionStep.Value) },
        )
    }
}

/** Text fields an extraction may read, by dotted path. */
private fun textFieldOptions(fields: List<CatalogFieldInfo>): List<String> {
    return fields.scalarPaths()
        .filter { field -> OperatorOptions.isTextType(fieldType = field.type) }
        .map { field -> field.id }
}

private fun defaultExtraction(fields: List<CatalogFieldInfo>): BuilderExtraction {
    return BuilderExtraction(
        sourceField = textFieldOptions(fields = fields).firstOrNull().orEmpty(),
        pattern = "",
        // Group 1 rather than 0: a pattern is written with a capture group because the author wants the
        // group, and `(.*)` around the whole match is the rarer intent.
        groupIndex = 1,
    )
}

/** The `$name` references of the requested kind. */
private fun variableOptions(fields: List<CatalogFieldInfo>, wantList: Boolean): List<String> {
    return fields
        .filter { field -> OperatorOptions.isVariableId(fieldId = field.id) }
        .filter { field -> OperatorOptions.isListVariableType(fieldType = field.type) == wantList }
        .map { field -> field.id }
}

private fun actionEcho(action: MutableBuilderAction): String {
    val extraction = action.extraction
    val prefix = if (extraction == null) "" else extractionEcho(extraction = extraction) + " "
    val argument = action.arguments.firstOrNull()?.takeIf { it.isNotBlank() }
    return prefix + action.name + (argument?.let { " $it" } ?: "")
}

private fun extractionEcho(extraction: BuilderExtraction): String {
    return "extract ${extraction.sourceField} regex(\"${extraction.pattern}\", ${extraction.groupIndex})"
}

private fun assignmentEcho(assignment: MutableBuilderVariable): String {
    val value = OperandText.toDsl(operand = assignment.expression)
    val name = assignment.name.ifBlank { "…" }
    return when (assignment.kind) {
        AssignmentKindAst.SET -> "set $name = $value"
        AssignmentKindAst.ADD -> "add $value to $name"
    }
}
