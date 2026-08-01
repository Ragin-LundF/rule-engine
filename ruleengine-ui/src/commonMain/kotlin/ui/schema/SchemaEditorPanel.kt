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
 * A composable panel for editing schemas, supporting both a visual editor and a YAML-based editor
 * with seamless synchronization between the two modes.
 *
 * @param yaml The initial YAML content to be displayed and edited in the panel.
 * @param fromYaml A function to parse the YAML content and convert it into an instance
 * of SchemaEditorState, representing the state of the schema editor.
 * @param toYaml A function to serialize the SchemaEditorState into a YAML string.
 * @param onYamlChange A callback invoked whenever the YAML content changes within the editor,
 * either due to user action or programmatic updates.
 * @param initialMode The starting mode of the editor, either visual or YAML-based. The default is
 * SchemaMode.VISUAL.
 * @param modifier A [Modifier] instance for styling and layout customization of the panel.
 * @param yamlEditor A customizable composable function for rendering the YAML editor. By default,
 * it provides a basic text field implementation for editing YAML content.
 */
@Composable
fun SchemaEditorPanel(
    yaml: String,
    fromYaml: (String) -> SchemaEditorState,
    toYaml: (SchemaEditorState) -> String,
    onYamlChange: (String) -> Unit,
    initialMode: SchemaMode = SchemaMode.VISUAL,
    modifier: Modifier = Modifier,
    /**
     * Draws which rules read which field. Supplied by the platform because the diagram renderer is
     * JVM-side; null keeps the "later phase" placeholder.
     */
    usagesContent: (@Composable () -> Unit)? = null,
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
    var mode by remember { mutableStateOf(value = initialMode) }
    var editorState by remember { mutableStateOf(value = fromYaml(yaml)) }
    var yamlText by remember { mutableStateOf(value = yaml) }
    var yamlError by remember { mutableStateOf<String?>(value = null) }

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
            SchemaMode.USAGES -> usagesContent?.invoke() ?: FieldUsagesPanel()
        }
    }
}

/**
 * Determines if the current schema editor state contains validation issues.
 * Validation issues include blank field paths or duplicate field paths.
 *
 * @return True if there are any blank or duplicate field paths in the editor state, false otherwise.
 */
private fun SchemaEditorState.hasValidationIssues(): Boolean {
    val paths = fields.map { it.path.trim() }.filter { it.isNotBlank() }
    val hasBlank = fields.any { it.path.isBlank() }
    val hasDuplicate = paths.size != paths.toSet().size
    return hasBlank || hasDuplicate
}

/**
 * A composable function for editing a visual schema, including its fields and properties,
 * within an arranged layout. Displays field definitions inside an editable table,
 * along with a title section.
 *
 * @param state The current state of the schema editor, including the schema name,
 *              fields, and read-only status.
 * @param onStateChange A callback function invoked with the updated schema state
 *                      when changes occur in the editor.
 */
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

/**
 * A composable function for editing and validating YAML schema. It includes features
 * for displaying errors, validation issues, and a custom YAML editor component.
 *
 * @param yaml The YAML content to be displayed and edited.
 * @param error An optional error message to be displayed if there are issues with the YAML.
 * @param validationIssues A flag indicating whether validation issues (e.g., blank or duplicate paths) exist.
 * @param onYamlChange Callback to handle changes to the YAML content.
 * @param yamlEditor A composable lambda for rendering the YAML editor. Provides the current text content,
 *        a callback for handling text changes, and a modifier for styling.
 */
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
