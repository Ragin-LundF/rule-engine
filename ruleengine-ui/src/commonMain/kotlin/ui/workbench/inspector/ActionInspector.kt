package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.actions.KnownActionArgTypes
import ui.actions.model.EditableAction
import ui.components.input.OrderedListEditor
import ui.components.input.model.OrderedListOption
import ui.util.Plurals

/**
 * The editing surface for one declared action.
 *
 * The fix here is a type error in the old editor, not a layout preference. `argTypes` is a **positional
 * parameter list**: `Validator` checks `def.argTypes.size` against the argument count and then matches
 * the type at each index. The table rendered it as a row of toggle chips over a `List<String>`, adding
 * and removing by value — a *set*. Three consequences, all reachable:
 *
 * - `audit(string, integer)` and `audit(integer, string)` are different declarations, and a chip row
 *   cannot tell them apart or produce one on purpose;
 * - `audit(string, string)` was **unreachable**, because a chip is either on or off;
 * - the order was whatever order the chips happened to be clicked, and unticking then re-ticking one
 *   silently rewrote the signature.
 *
 * So the arguments are an [OrderedListEditor] with `allowDuplicates`, and the signature is echoed above
 * it as the call a rule actually writes.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ActionInspector(
    action: EditableAction,
    onActionChange: (EditableAction) -> Unit,
    modifier: Modifier = Modifier,
    usages: Int? = null,
    editable: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= WIDE_FROM

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(state = rememberScrollState())
                .padding(horizontal = if (wide) 18.dp else 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            InspectorHeading(title = signatureOf(action = action), kind = "Action")
            ActionIdentity(action = action, editable = editable, wide = wide, onActionChange = onActionChange)
            ActionArguments(action = action, editable = editable, onActionChange = onActionChange)
            ActionSpelling(action = action)
            ActionUsage(usages = usages)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ActionIdentity(
    action: EditableAction,
    editable: Boolean,
    wide: Boolean,
    onActionChange: (EditableAction) -> Unit,
) {
    InspectorGroup(title = "Identity")
    InspectorTextField(
        label = "name",
        value = action.name,
        placeholder = "decision",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onActionChange(action.copy(name = text)) },
    )
    InspectorTextField(
        label = "purpose",
        value = action.purpose,
        placeholder = "what it is for",
        enabled = editable,
        wide = wide,
        onValueChange = { text -> onActionChange(action.copy(purpose = text)) },
    )
    // Only once there is something to lose. Shown for every action it would be a standing complaint
    // about a field most people leave empty.
    if (action.purpose.isNotBlank()) {
        InspectorNote(
            text = "The loader ignores purpose and reads it back as empty, so this text will not " +
                "survive a reload yet.",
            warning = true,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ActionArguments(
    action: EditableAction,
    editable: Boolean,
    onActionChange: (EditableAction) -> Unit,
) {
    InspectorGroup(title = "Arguments", note = if (action.argTypes.isEmpty()) "none" else "positional")
    OrderedListEditor(
        items = action.argTypes,
        options = KnownActionArgTypes.map { type ->
            OrderedListOption(value = type, what = argTypeMeaning(type = type))
        },
        onMove = { from, to -> onActionChange(action.copy(argTypes = action.argTypes.moved(from, to))) },
        onRemove = { index ->
            onActionChange(action.copy(argTypes = action.argTypes.filterIndexed { at, _ -> at != index }))
        },
        onAdd = { value -> onActionChange(action.copy(argTypes = action.argTypes + value)) },
        emptyText = "No argument. A rule writes just ${action.name.ifBlank { "the name" }}.",
        enabled = editable,
        // A parameter list may repeat a type; a normalizer chain may not.
        allowDuplicates = true,
        positionLabel = { index -> "arg ${index + 1}" },
    )
    InspectorNote(
        text = "The order is the declaration: audit(string, integer) is not audit(integer, string), " +
            "and two arguments of the same type are allowed.",
    )
}

@Suppress("FunctionNaming")
@Composable
private fun ActionSpelling(action: EditableAction) {
    InspectorGroup(title = "How a rule writes it")
    InspectorEcho(text = "then " + spellCall(action = action))
    if (action.argTypes.size > 1) {
        InspectorNote(
            text = "The Builder fills in one argument, so a rule emitting this has to be written in " +
                "the Code view.",
            warning = true,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ActionUsage(usages: Int?) {
    val count = usages ?: return
    InspectorGroup(title = "Emitted by", note = "$count rule${Plurals.suffix(count = count)}")
    if (count == 0) {
        InspectorNote(text = "No loaded rule emits this action.", warning = true)
    }
}

/** The call as a reader recognises it: `audit(string, integer)`. */
private fun signatureOf(action: EditableAction): String =
    action.name.ifBlank { "…" } + "(" + action.argTypes.joinToString(separator = ", ") + ")"

/** The clause a rule writes, with a plausible value per declared position. */
private fun spellCall(action: EditableAction): String {
    val name = action.name.ifBlank { "…" }
    if (action.argTypes.isEmpty()) return name
    return name + " " + action.argTypes.joinToString(separator = " ") { type -> exampleValue(type = type) }
}

private fun exampleValue(type: String): String = when (type) {
    "string" -> "\"approved\""
    "integer" -> "250"
    "decimal" -> "0.75"
    "variable_string" -> "\$why"
    "variable_list" -> "\$topics"
    else -> "<$type>"
}

private fun argTypeMeaning(type: String): String = when (type) {
    "string" -> "Text in double quotes."
    "integer" -> "A whole number, unquoted."
    "decimal" -> "A number with decimal places, unquoted."
    "variable_string" -> "A reference to a value published with set."
    "variable_list" -> "A reference to a list accumulated with add."
    else -> ""
}

/** [this] with the item at [from] moved to [to]. Out-of-range indices leave the list alone. */
private fun List<String>.moved(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices || from == to) return this
    val copy = toMutableList()
    copy.add(index = to, element = copy.removeAt(index = from))
    return copy
}

private val WIDE_FROM = 480.dp
