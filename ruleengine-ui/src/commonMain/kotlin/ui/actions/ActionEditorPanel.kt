package ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.actions.canvas.ActionsCanvas
import ui.actions.model.ActionEditorState
import ui.components.SectionTitle
import ui.editor.YamlEditorPane
import ui.editor.yaml.YamlModelSync
import ui.workbench.model.mode.ActionMode

/**
 * A composable function representing the Action Editor Panel. It allows users
 * to edit an action schema either visually, in YAML format, or explore its usages.
 *
 * @param yaml The initial YAML input representing the action schema.
 * @param fromYaml A function to convert the given YAML string into an [ActionEditorState].
 * @param toYaml A function to serialize an [ActionEditorState] back into a YAML string.
 * @param onYamlChange A callback invoked whenever the YAML content changes.
 * @param initialMode The default mode for the editor panel, either VISUAL or YAML.
 * @param modifier The Modifier to be applied to the layout of the editor panel.
 * @param yamlEditor A custom composable for rendering the YAML editor. It takes the current
 *                   YAML [TextFieldValue], a callback for changes, and a modifier.
 */
@Composable
fun ActionEditorPanel(
    /** The model and the text, owned by the caller — see [ui.schema.SchemaEditorPanel]. */
    sync: YamlModelSync<ActionEditorState>,
    /** Which tab is open — see [ui.schema.SchemaEditorPanel]. */
    mode: ActionMode,

    modifier: Modifier = Modifier,
    /** Shows one action in the Inspector. The whole row is the target now. */
    onInspectAction: ((name: String) -> Unit)? = null,
    /** The action the Inspector is on, highlighted in the canvas. */
    selectedActionName: String? = null,
    /** How many loaded rules emit each action. */
    emittedBy: Map<String, Int> = emptyMap(),
    onMessage: (String) -> Unit = {},
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
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (mode) {
            ActionMode.VISUAL -> ActionsCanvas(
                state = sync.state,
                onStateChange = { edited -> sync.state = edited },
                modifier = Modifier.fillMaxSize(),
                selectedName = selectedActionName,
                onSelectName = { name -> onInspectAction?.invoke(name) },
                emittedBy = emittedBy,
                onMessage = onMessage,
            )
            ActionMode.YAML -> YamlActionEditor(
                yaml = sync.yaml,
                error = sync.error,
                validationIssues = sync.state.hasValidationIssues(),
                onYamlChange = { newText ->
                    sync.yaml = newText
                    sync.error = null
                },
                yamlEditor = yamlEditor,
            )
        }
    }
}

internal fun ActionEditorState.hasValidationIssues(): Boolean {
    val names = actions.map { it.name.trim() }.filter { it.isNotBlank() }
    val hasBlank = actions.any { it.name.isBlank() }
    val hasDuplicate = names.size != names.toSet().size
    return hasBlank || hasDuplicate
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

