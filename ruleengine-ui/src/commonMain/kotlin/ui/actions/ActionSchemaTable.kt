package ui.actions

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.actions.model.ActionEditorState
import ui.actions.model.EditableAction
import ui.components.HeaderCell
import ui.components.ToggleChip

/**
 * Visual action-schema editor table.
 *
 * Displays [state] as an editable list of action rows. Each row exposes:
 * - name (text field)
 * - argument types (toggle chips)
 * - purpose (text field)
 * - delete button
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun ActionSchemaTable(
    state: ActionEditorState,
    onStateChange: (ActionEditorState) -> Unit,
    modifier: Modifier = Modifier,
    /** Shows the action in the Inspector. Null hides the button. */
    onInspectAction: ((name: String) -> Unit)? = null,
) {
    val editable = !state.isReadOnly

    Column(modifier = modifier.fillMaxWidth()) {
        if (state.isReadOnly) {
            Text(
                text = "Read-only preview — YAML contains unsupported constructs. Edit YAML directly.",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Name", Modifier.weight(2f))
            HeaderCell("Argument types", Modifier.weight(3f))
            HeaderCell("Purpose", Modifier.weight(3f))
            // One spacer per trailing button, so the columns line up in every combination.
            if (onInspectAction != null) Spacer(Modifier.width(36.dp))
            if (editable) Spacer(Modifier.width(36.dp))
        }

        Spacer(Modifier.height(2.dp))

        val duplicateNames = state.actions
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        // ── rows ──────────────────────────────────────────────────────────────
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.actions) { index, action ->
                    ActionRow(
                        action = action,
                        editable = editable,
                        isDuplicate = action.name.isNotBlank() && action.name in duplicateNames,
                        onActionChange = { updated ->
                            val newActions = state.actions.toMutableList().also { it[index] = updated }
                            onStateChange(state.copy(actions = newActions))
                        },
                        onDelete = {
                            val newActions = state.actions.toMutableList().also { it.removeAt(index) }
                            onStateChange(state.copy(actions = newActions))
                        },
                        onInspect = onInspectAction?.let { inspect -> { inspect(action.name) } },
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState = listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }

        if (duplicateNames.isNotEmpty()) {
            Text(
                text = "Error: duplicate action names found — ${duplicateNames.joinToString()}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        if (editable) {
            Spacer(Modifier.height(8.dp))
            AddActionDropdown(
                onAdd = { template ->
                    onStateChange(state.copy(actions = state.actions + template))
                },
            )
        }
    }
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun ActionRow(
    action: EditableAction,
    editable: Boolean,
    isDuplicate: Boolean,
    onActionChange: (EditableAction) -> Unit,
    onDelete: () -> Unit,
    onInspect: (() -> Unit)? = null,
) {
    val fieldColors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = TextPrimary,
        backgroundColor = BgSurface,
        focusedBorderColor = if (isDuplicate) MaterialTheme.colors.error else PrimaryBlue,
        unfocusedBorderColor = if (isDuplicate) MaterialTheme.colors.error else BorderColor,
        cursorColor = PrimaryBlue,
        placeholderColor = TextMuted,
        disabledTextColor = TextSecondary,
        disabledBorderColor = BorderColor,
    )

    val borderColor = if (isDuplicate) MaterialTheme.colors.error else BorderColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(4.dp))
            .background(BgSurface, shape = RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = action.name,
                onValueChange = { onActionChange(action.copy(name = it)) },
                modifier = Modifier.weight(2f),
                enabled = editable,
                singleLine = true,
                placeholder = { Text("name", color = TextMuted) },
                colors = fieldColors,
            )
            Row(
                modifier = Modifier.weight(3f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KnownActionArgTypes.forEach { type ->
                    ToggleChip(
                        label = type,
                        selected = type in action.argTypes,
                        onClick = {
                            if (editable) {
                                val updated = if (type in action.argTypes) {
                                    action.argTypes - type
                                } else {
                                    action.argTypes + type
                                }
                                onActionChange(action.copy(argTypes = updated))
                            }
                        },
                        enabled = editable,
                    )
                }
            }
            OutlinedTextField(
                value = action.purpose,
                onValueChange = { onActionChange(action.copy(purpose = it)) },
                modifier = Modifier.weight(3f),
                enabled = editable,
                singleLine = true,
                placeholder = { Text("purpose", color = TextMuted) },
                colors = fieldColors,
            )
            // A button rather than a click on the row: the cells are text fields, and a row-wide click
            // target would fight the editing it sits on top of.
            if (onInspect != null) {
                // Held to the 36.dp the header reserves for a trailing button. A bare `TextButton`
                // claims 64.dp, and the width it takes comes out of the weighted columns beside it —
                // enough to push the longest argument-type chip into one letter per line.
                TextButton(
                    onClick = onInspect,
                    modifier = Modifier.width(width = 36.dp),
                    contentPadding = PaddingValues(all = 0.dp),
                ) {
                    Text("ⓘ", color = PrimaryBlue)
                }
            }
            if (editable) {
                TextButton(onClick = onDelete) {
                    Text("✕", color = TextMuted)
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AddActionDropdown(
    onAdd: (EditableAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("+ Add action", color = PrimaryBlue)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val templates = listOf(
                "Blank action" to EditableAction(),
                "Label action" to EditableAction(
                    name = "label",
                    argTypes = listOf("string"),
                    purpose = "Categorise the match with a label",
                ),
                "Score action" to EditableAction(
                    name = "score",
                    argTypes = listOf("integer"),
                    purpose = "Add an integer score",
                ),
                "Category action" to EditableAction(
                    name = "category",
                    argTypes = listOf("string"),
                    purpose = "Assign a category",
                ),
            )
            templates.forEach { (label, template) ->
                DropdownMenuItem(onClick = {
                    onAdd(template)
                    expanded = false
                }) {
                    Text(text = label)
                }
            }
        }
    }
}

