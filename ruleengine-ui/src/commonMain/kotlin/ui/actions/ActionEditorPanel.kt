package ui.actions

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
import ui.editor.YamlEditorPane
import ui.workbench.ActionMode

/**
 * A composable function representing the Action Editor Panel. It allows users
 * to edit an action schema either visually, in YAML format, or explore its usages.
 *
 * @param yaml The initial YAML input representing the action schema.
 * @param fromYaml A function to convert the given YAML string into an [ActionEditorState].
 * @param toYaml A function to serialize an [ActionEditorState] back into a YAML string.
 * @param onYamlChange A callback invoked whenever the YAML content changes.
 * @param initialMode The default mode for the editor panel, either VISUAL, YAML, or USAGES.
 * @param modifier The Modifier to be applied to the layout of the editor panel.
 * @param yamlEditor A custom composable for rendering the YAML editor. It takes the current
 *                   YAML [TextFieldValue], a callback for changes, and a modifier.
 */
@Composable
fun ActionEditorPanel(
    yaml: String,
    fromYaml: (String) -> ActionEditorState,
    toYaml: (ActionEditorState) -> String,
    onYamlChange: (String) -> Unit,
    initialMode: ActionMode = ActionMode.VISUAL,
    modifier: Modifier = Modifier,
    /**
     * Draws which rules emit which action. Supplied by the platform because the diagram renderer is
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

    /**
     * The model as it was last read from YAML — see [ui.schema.SchemaEditorPanel] for why.
     *
     * Regenerating YAML from the model drops the author's comments and formatting, so merely opening
     * this tab must not count as an edit; otherwise the next project save rewrites the file.
     */
    var loadedState by remember { mutableStateOf(value = editorState) }

    // External YAML changes (e.g. project load) should pull into the local model.
    LaunchedEffect(key1 = yaml) {
        if (yaml != yamlText) {
            yamlText = yaml
            editorState = fromYaml(yaml)
            loadedState = editorState
            yamlError = null
        }
    }

    // Visual/editor changes push to YAML only when the model is valid and actually different.
    LaunchedEffect(key1 = editorState, key2 = mode) {
        if (mode == ActionMode.YAML) return@LaunchedEffect
        if (editorState.hasValidationIssues()) return@LaunchedEffect
        if (editorState == loadedState) return@LaunchedEffect
        val generated = runCatching { toYaml(editorState) }.getOrNull() ?: return@LaunchedEffect
        if (generated != yamlText) {
            yamlText = generated
            onYamlChange(generated)
        }
    }

    // YAML edits parse back to the visual model when valid (debounced).
    LaunchedEffect(key1 = yamlText, key2 = mode) {
        if (mode != ActionMode.YAML) return@LaunchedEffect
        delay(timeMillis = 500)
        val parsed = runCatching { fromYaml(yamlText) }.getOrNull()
        if (parsed != null && !parsed.isReadOnly) {
            editorState = parsed
            yamlError = null
        } else {
            yamlError = "Invalid YAML: could not parse action schema"
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionModeTabs(
            current = mode,
            onSelect = { newMode ->
                if (newMode == ActionMode.YAML && mode != ActionMode.YAML) {
                    if (!editorState.hasValidationIssues()) {
                        yamlText = runCatching { toYaml(editorState) }.getOrNull() ?: yamlText
                        onYamlChange(yamlText)
                    }
                }
                if (newMode != ActionMode.YAML && mode == ActionMode.YAML) {
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
            ActionMode.VISUAL -> VisualActionEditor(
                state = editorState,
                onStateChange = { editorState = it },
            )
            ActionMode.YAML -> YamlActionEditor(
                yaml = yamlText,
                error = yamlError,
                validationIssues = editorState.hasValidationIssues(),
                onYamlChange = { newText ->
                    yamlText = newText
                    yamlError = null
                },
                yamlEditor = yamlEditor,
            )
            ActionMode.USAGES -> usagesContent?.invoke() ?: ActionUsagesPanel()
        }
    }
}

private fun ActionEditorState.hasValidationIssues(): Boolean {
    val names = actions.map { it.name.trim() }.filter { it.isNotBlank() }
    val hasBlank = actions.any { it.name.isBlank() }
    val hasDuplicate = names.size != names.toSet().size
    return hasBlank || hasDuplicate
}

@Composable
private fun VisualActionEditor(
    state: ActionEditorState,
    onStateChange: (ActionEditorState) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "ACTIONS")
        ActionSchemaTable(
            state = state,
            onStateChange = onStateChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun YamlActionEditor(
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
                text = "Visual editor contains blank or duplicate names. These rows are hidden in YAML until resolved.",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
            )
        }
        YamlEditorPane(
            yaml = yaml,
            onYamlChange = onYamlChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            editor = yamlEditor,
        )
    }
}

@Composable
private fun ActionUsagesPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "USAGE")
        Text(
            text = "Action usage across rules will be shown here in a later phase.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}
