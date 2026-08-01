package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ui.actions.ActionEditorPanel
import ui.actions.ActionEditorState

/**
 * Action schema editor area.
 */
@Composable
fun ActionsAreaScreen(
    actionsYaml: String,
    fromYaml: (String) -> ActionEditorState,
    toYaml: (ActionEditorState) -> String,
    onActionsYamlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    usagesContent: (@Composable () -> Unit)? = null,
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
        yaml = actionsYaml,
        fromYaml = fromYaml,
        toYaml = toYaml,
        onYamlChange = onActionsYamlChange,
        modifier = modifier,
        usagesContent = usagesContent,
        yamlEditor = yamlEditor,
    )
}
