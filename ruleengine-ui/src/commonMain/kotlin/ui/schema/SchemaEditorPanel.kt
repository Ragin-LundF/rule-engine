package ui.schema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ui.TextSecondary
import ui.components.SectionTitle
import ui.workbench.SchemaMode

/**
 * Visual field-schema editor with Visual / YAML / Usages tabs.
 *
 * The editor keeps its own [editorState] while the user edits. It only pushes YAML
 * upstream when the local state is valid (no blank or duplicate paths). This
 * prevents blank rows from disappearing while typing and avoids duplicate-key
 * round-trip losses.
 */
@Composable
fun SchemaEditorPanel(
    yaml: String,
    fromYaml: (String) -> SchemaEditorState,
    toYaml: (SchemaEditorState) -> String,
    onYamlChange: (String) -> Unit,
    initialMode: SchemaMode = SchemaMode.VISUAL,
    modifier: Modifier = Modifier,
    yamlEditor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit = { value, onValueChange, fieldModifier ->
        OutlinedTextField(
            value = value.text,
            onValueChange = { onValueChange(TextFieldValue(text = it)) },
            modifier = fieldModifier,
            textStyle = MaterialTheme.typography.body2,
        )
    },
) {
    var mode by remember { mutableStateOf(initialMode) }
    var editorState by remember { mutableStateOf(fromYaml(yaml)) }
    var yamlText by remember { mutableStateOf(yaml) }
    var yamlError by remember { mutableStateOf<String?>(null) }

    // External YAML changes (e.g. manifest load) should pull into the local model.
    LaunchedEffect(key1 = yaml) {
        if (yaml != yamlText) {
            yamlText = yaml
            editorState = fromYaml(yaml)
            yamlError = null
        }
    }

    // Visual/editor changes push to YAML only when the model is valid.
    LaunchedEffect(key1 = editorState, key2 = mode) {
        if (mode == SchemaMode.YAML) return@LaunchedEffect
        if (editorState.hasValidationIssues()) return@LaunchedEffect
        val generated = runCatching { toYaml(editorState) }.getOrNull() ?: return@LaunchedEffect
        if (generated != yamlText) {
            yamlText = generated
            onYamlChange(generated)
        }
    }

    // YAML edits parse back to the visual model when valid (debounced).
    LaunchedEffect(key1 = yamlText, key2 = mode) {
        if (mode != SchemaMode.YAML) return@LaunchedEffect
        delay(timeMillis = 500)
        val parsed = runCatching { fromYaml(yamlText) }.getOrNull()
        if (parsed != null && !parsed.isReadOnly) {
            editorState = parsed
            yamlError = null
        } else {
            yamlError = "Invalid YAML: could not parse field schema"
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SchemaModeTabs(
            current = mode,
            onSelect = { newMode ->
                if (newMode == SchemaMode.YAML && mode != SchemaMode.YAML) {
                    if (!editorState.hasValidationIssues()) {
                        yamlText = runCatching { toYaml(editorState) }.getOrNull() ?: yamlText
                        onYamlChange(yamlText)
                    }
                }
                if (newMode != SchemaMode.YAML && mode == SchemaMode.YAML) {
                    val generated = runCatching { toYaml(editorState) }.getOrNull()
                    if (generated != null) {
                        yamlText = generated
                        onYamlChange(yamlText)
                    }
                }
                mode = newMode
            },
        )

        when (mode) {
            SchemaMode.VISUAL -> VisualSchemaEditor(
                state = editorState,
                onStateChange = { editorState = it },
            )
            SchemaMode.YAML -> YamlSchemaEditor(
                yaml = yamlText,
                error = yamlError,
                validationIssues = editorState.hasValidationIssues(),
                onYamlChange = { newText ->
                    yamlText = newText
                    yamlError = null
                },
                yamlEditor = yamlEditor,
            )
            SchemaMode.USAGES -> FieldUsagesPanel()
        }
    }
}

private fun SchemaEditorState.hasValidationIssues(): Boolean {
    val paths = fields.map { it.path.trim() }.filter { it.isNotBlank() }
    val hasBlank = fields.any { it.path.isBlank() }
    val hasDuplicate = paths.size != paths.toSet().size
    return hasBlank || hasDuplicate
}

@Composable
private fun VisualSchemaEditor(
    state: SchemaEditorState,
    onStateChange: (SchemaEditorState) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "FIELDS")
        FieldSchemaTable(
            state = state,
            onStateChange = onStateChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun YamlSchemaEditor(
    yaml: String,
    error: String?,
    validationIssues: Boolean,
    onYamlChange: (String) -> Unit,
    yamlEditor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(text = "YAML")
            Text(
                text = "Auto-reloads on valid YAML",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
            )
        }
        if (validationIssues) {
            Text(
                text = "Visual editor contains blank or duplicate paths. These rows are hidden in YAML until resolved.",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
            )
        }
        yamlEditor(
            TextFieldValue(text = yaml),
            { newValue ->
                onYamlChange(newValue.text)
            },
            Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun FieldUsagesPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "USAGE")
        Text(
            text = "Field usage across conditions will be shown here in a later phase.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}
