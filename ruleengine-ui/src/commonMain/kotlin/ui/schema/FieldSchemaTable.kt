package ui.schema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.BgElevated
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary

/**
 * Visual schema editor table.
 *
 * Displays [state] as an editable list of field rows. Each row exposes:
 * - path (text field)
 * - alias (text field)
 * - type (dropdown)
 * - normalizers (toggle chips via [NormalizerSelector])
 * - operators (toggle chips via [OperatorSelector])
 * - delete button
 *
 * When [state.isReadOnly] is true the table is rendered in preview mode with
 * no editing controls.
 *
 * @param state       Current [SchemaEditorState].
 * @param onStateChange  Called with the updated state on every edit.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
fun FieldSchemaTable(
    state: SchemaEditorState,
    onStateChange: (SchemaEditorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = !state.isReadOnly

    Column(modifier = modifier.fillMaxWidth()) {
        // ── header ────────────────────────────────────────────────────────────
        if (state.isReadOnly) {
            Text(
                text = "Read-only preview — YAML contains unsupported constructs. Edit YAML directly.",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // ── column headers ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("Path", Modifier.weight(2f))
            HeaderCell("Alias", Modifier.weight(1.5f))
            HeaderCell("Type", Modifier.weight(1.5f))
            HeaderCell("Normalizers", Modifier.weight(3f))
            HeaderCell("Operators", Modifier.weight(3f))
            if (editable) Spacer(Modifier.width(36.dp))
        }

        Spacer(Modifier.height(2.dp))

        // ── rows ──────────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(state.fields) { index, field ->
                FieldRow(
                    field = field,
                    editable = editable,
                    onFieldChange = { updated ->
                        val newFields = state.fields.toMutableList().also { it[index] = updated }
                        onStateChange(state.copy(fields = newFields))
                    },
                    onDelete = {
                        val newFields = state.fields.toMutableList().also { it.removeAt(index) }
                        onStateChange(state.copy(fields = newFields))
                    },
                )
            }
        }

        // ── add row button ────────────────────────────────────────────────────
        if (editable) {
            Spacer(Modifier.height(8.dp))
            AddFieldDropdown(
                onAdd = { template ->
                    onStateChange(state.copy(fields = state.fields + template))
                },
            )
        }
    }
}

// ── private helpers ───────────────────────────────────────────────────────────

@Suppress("FunctionNaming")
@Composable
private fun AddFieldDropdown(
    onAdd: (EditableField) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("+ Add field", color = PrimaryBlue)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val templates = listOf(
                "Blank field" to EditableField(),
                "Text field" to EditableField(
                    path = "field",
                    type = SchemaFieldType.TEXT,
                    operators = listOf("equals", "contains"),
                ),
                "Integer field" to EditableField(
                    path = "count",
                    type = SchemaFieldType.INTEGER,
                    operators = listOf("equals", "gt", "gte", "lt", "lte", "between"),
                ),
                "Decimal field" to EditableField(
                    path = "amount",
                    type = SchemaFieldType.DECIMAL,
                    operators = listOf("equals", "gt", "gte", "lt", "lte", "between"),
                ),
                "Boolean field" to EditableField(
                    path = "flag",
                    type = SchemaFieldType.BOOLEAN,
                    operators = listOf("equals"),
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

@Suppress("FunctionNaming")
@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.caption,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        fontSize = 11.sp,
    )
}

@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun FieldRow(
    field: EditableField,
    editable: Boolean,
    onFieldChange: (EditableField) -> Unit,
    onDelete: () -> Unit,
) {
    val fieldColors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = TextPrimary,
        backgroundColor = BgSurface,
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = BorderColor,
        cursorColor = PrimaryBlue,
        placeholderColor = TextMuted,
        disabledTextColor = TextSecondary,
        disabledBorderColor = BorderColor,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(4.dp))
            .background(BgSurface, shape = RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── top row: path / alias / type / delete ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = field.path,
                onValueChange = { onFieldChange(field.copy(path = it)) },
                modifier = Modifier.weight(2f),
                enabled = editable,
                singleLine = true,
                placeholder = { Text("field.path", color = TextMuted) },
                colors = fieldColors,
            )
            OutlinedTextField(
                value = field.alias,
                onValueChange = { onFieldChange(field.copy(alias = it)) },
                modifier = Modifier.weight(1.5f),
                enabled = editable,
                singleLine = true,
                placeholder = { Text("alias", color = TextMuted) },
                colors = fieldColors,
            )
            TypeDropdown(
                selected = field.type,
                enabled = editable,
                onSelect = { onFieldChange(field.copy(type = it)) },
                modifier = Modifier.weight(1.5f),
            )
            if (editable) {
                TextButton(onClick = onDelete) {
                    Text("✕", color = TextMuted)
                }
            }
        }

        // ── normalizers ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Normalizers",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                modifier = Modifier.width(80.dp).padding(top = 6.dp),
            )
            NormalizerSelector(
                selected = field.normalizers,
                onToggle = { norm ->
                    val updated = if (norm in field.normalizers) {
                        field.normalizers - norm
                    } else {
                        field.normalizers + norm
                    }
                    onFieldChange(field.copy(normalizers = updated))
                },
                enabled = editable,
                modifier = Modifier.weight(1f),
            )
        }

        // ── operators ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Operators",
                style = MaterialTheme.typography.caption,
                color = TextMuted,
                modifier = Modifier.width(80.dp).padding(top = 6.dp),
            )
            OperatorSelector(
                selected = field.operators,
                onToggle = { op ->
                    val updated = if (op in field.operators) {
                        field.operators - op
                    } else {
                        field.operators + op
                    }
                    onFieldChange(field.copy(operators = updated))
                },
                enabled = editable,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TypeDropdown(
    selected: SchemaFieldType,
    enabled: Boolean,
    onSelect: (SchemaFieldType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = {
                if (enabled) {
                    TextButton(onClick = { expanded = true }) {
                        Text("▾", color = TextMuted)
                    }
                }
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = TextPrimary,
                backgroundColor = BgSurface,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BorderColor,
                cursorColor = Color.Transparent,
                disabledTextColor = TextSecondary,
                disabledBorderColor = BorderColor,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SchemaFieldType.entries.forEach { type ->
                DropdownMenuItem(onClick = {
                    onSelect(type)
                    expanded = false
                }) {
                    Text(text = type.displayName, color = TextPrimary)
                }
            }
        }
    }
}
