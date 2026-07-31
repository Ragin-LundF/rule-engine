package ui.schema

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import ui.builder.components.DropdownSelector

/**
 * Renders a composable table for editing a schema, displaying fields with their respective
 * properties such as path, alias, type, normalizers, and operators. Includes features like
 * duplicate path detection, row addition, and read-only mode.
 *
 * @param state The current state of the schema editor, including schema name, field definitions,
 *              and read-only status.
 * @param onStateChange A callback function invoked when the schema editor's state is updated.
 *                      Provides the updated state as a parameter.
 * @param modifier Modifier for customizing the layout or appearance of the table.
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
                .background(color = BgElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell(text = "Path", modifier = Modifier.weight(weight = 2f))
            HeaderCell(text = "Alias", modifier = Modifier.weight(weight = 1.5f))
            HeaderCell(text = "Type", modifier = Modifier.weight(weight = 1.5f))
            HeaderCell(text = "Normalizers", modifier = Modifier.weight(weight = 3f))
            HeaderCell(text = "Operators", modifier = Modifier.weight(weight = 3f))
            if (editable) Spacer(modifier = Modifier.width(width = 36.dp))
        }

        Spacer(modifier = Modifier.height(height = 2.dp))

        val duplicatePaths = state.fields
            .map { it.path.trim() }
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
                verticalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                itemsIndexed(state.fields) { index, field ->
                    FieldRow(
                        field = field,
                        editable = editable,
                        isDuplicate = field.path.isNotBlank() && field.path in duplicatePaths,
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
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState = listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }

        if (duplicatePaths.isNotEmpty()) {
            Text(
                text = "Error: duplicate paths found — ${duplicatePaths.joinToString()}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(vertical = 4.dp),
            )
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
            FieldTemplates.forEach { (label, template) ->
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
    isDuplicate: Boolean,
    onFieldChange: (EditableField) -> Unit,
    onDelete: () -> Unit,
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
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(size = 4.dp))
            .background(color = BgSurface, shape = RoundedCornerShape(size = 4.dp))
            .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        // ── top row: path / alias / type / delete ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = field.path,
                onValueChange = { onFieldChange(field.copy(path = it)) },
                modifier = Modifier.weight(weight = 2f),
                enabled = editable,
                singleLine = true,
                placeholder = { Text("field.path", color = TextMuted) },
                colors = fieldColors,
            )
            OutlinedTextField(
                value = field.alias,
                onValueChange = { onFieldChange(field.copy(alias = it)) },
                modifier = Modifier.weight(weight = 1.5f),
                enabled = editable,
                singleLine = true,
                placeholder = { Text("alias", color = TextMuted) },
                colors = fieldColors,
            )
            TypeDropdown(
                selected = field.type,
                // A format only means something on a date type; carrying a stale one over to another type
                // would emit YAML the loader rejects.
                onSelect = { onFieldChange(field.copy(type = it, format = if (it.isTemporal) field.format else "")) },
                modifier = Modifier.weight(weight = 1.5f),
            )
            if (editable) {
                TextButton(onClick = onDelete) {
                    Text("✕", color = TextMuted)
                }
            }
        }

        // A structure is navigated into, so normalizers and operators do not apply to it; its nested
        // members are edited as indented child rows instead.
        if (field.type.isStructure) {
            NestedFieldsSection(
                field = field,
                editable = editable,
                onFieldChange = onFieldChange,
            )
            return@Column
        }

        // ── date format ───────────────────────────────────────────────────
        if (field.type.isTemporal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Format",
                    style = MaterialTheme.typography.caption,
                    color = TextMuted,
                    modifier = Modifier.width(80.dp),
                )
                OutlinedTextField(
                    value = field.format,
                    onValueChange = { onFieldChange(field.copy(format = it)) },
                    modifier = Modifier.weight(1f),
                    enabled = editable,
                    singleLine = true,
                    placeholder = { Text(formatPlaceholder(type = field.type), color = TextMuted) },
                    colors = fieldColors,
                )
            }
        }

        // ── normalizers ───────────────────────────────────────────────────
        // Only text values are normalized by the engine, so the row would be inert on other types.
        if (field.type.isNormalizable) {
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
                type = field.type,
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

/** Hint shown in the empty format box: a pattern example, plus what leaving it empty means. */
private fun formatPlaceholder(type: SchemaFieldType): String {
    return if (type == SchemaFieldType.DATE_TIME) {
        "dd.MM.yyyy HH:mm — optional, ISO if empty"
    } else {
        "dd.MM.yyyy — optional, ISO if empty"
    }
}

/**
 * Nested members of a collection or object field, rendered as indented child rows.
 *
 * [FieldRow] is reused recursively, so a member that is itself a structure gets the same treatment at
 * the next level down and nesting depth is unbounded.
 */
@Suppress("FunctionNaming")
@Composable
private fun NestedFieldsSection(
    field: EditableField,
    editable: Boolean,
    onFieldChange: (EditableField) -> Unit,
) {
    var collapsed by remember { mutableStateOf(value = false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { collapsed = !collapsed }) {
            Text(
                text = if (collapsed) "▸ ${field.fields.size} nested field(s)" else "▾ nested fields",
                style = MaterialTheme.typography.caption,
                color = PrimaryBlue,
            )
        }
    }

    if (collapsed) return

    Column(
        modifier = Modifier.padding(start = NESTED_INDENT),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        field.fields.forEachIndexed { index, nested ->
            FieldRow(
                field = nested,
                editable = editable,
                isDuplicate = field.fields.count { it.path.isNotBlank() && it.path == nested.path } > 1,
                onFieldChange = { updated ->
                    onFieldChange(
                        field.copy(
                            fields = field.fields.toMutableList().also { it[index] = updated },
                        )
                    )
                },
                onDelete = {
                    onFieldChange(field.copy(fields = field.fields.filterIndexed { i, _ -> i != index }))
                },
            )
        }

        if (editable) {
            TextButton(
                onClick = {
                    onFieldChange(field.copy(fields = field.fields + EditableField(path = "field")))
                },
            ) {
                Text(text = "+ nested field", color = PrimaryBlue, style = MaterialTheme.typography.caption)
            }
        }
    }
}

/** Indentation applied per nesting level of the schema table. */
private val NESTED_INDENT = 16.dp

@Suppress("FunctionNaming")
@Composable
private fun TypeDropdown(
    selected: SchemaFieldType,
    onSelect: (SchemaFieldType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = SchemaFieldType.entries.map { it.displayName }
    DropdownSelector(
        selected = selected.displayName,
        options = options,
        onSelected = { displayName ->
            SchemaFieldType.entries
                .firstOrNull { it.displayName == displayName }
                ?.let { onSelect(it) }
        },
        modifier = modifier,
        placeholder = "type",
    )
}
