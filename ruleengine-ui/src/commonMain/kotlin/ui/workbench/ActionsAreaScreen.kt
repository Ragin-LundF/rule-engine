package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ui.actions.ActionEditorPanel
import ui.actions.model.ActionEditorState
import ui.editor.yaml.YamlModelSync
import ui.workbench.model.mode.ActionMode

/**
 * Action schema editor area.
 */
@Composable
fun ActionsAreaScreen(
    sync: YamlModelSync<ActionEditorState>,
    mode: ActionMode,

    modifier: Modifier = Modifier,
    onInspectAction: ((name: String) -> Unit)? = null,
    selectedActionName: String? = null,
    emittedBy: Map<String, Int> = emptyMap(),
    onMessage: (String) -> Unit = {},
    yamlEditor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit = { value, onValueChange, fieldModifier ->
        androidx.compose.material.OutlinedTextField(
            value = value.text,
            onValueChange = { onValueChange(TextFieldValue(text = it)) },
            modifier = fieldModifier,
            textStyle = androidx.compose.material.MaterialTheme.typography.body2,
        )
    },
) {
    ActionEditorPanel(
        sync = sync,
        mode = mode,
        modifier = modifier,
        onInspectAction = onInspectAction,
        selectedActionName = selectedActionName,
        emittedBy = emittedBy,
        onMessage = onMessage,
        yamlEditor = yamlEditor,
    )
}
