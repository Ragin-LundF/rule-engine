package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ui.editor.yaml.YamlModelSync
import ui.schema.SchemaEditorPanel
import ui.schema.model.SchemaEditorState
import ui.workbench.model.mode.SchemaMode

/**
 * Field Schema editor area.
 *
 * Replaces the earlier placeholder with a real Visual/YAML editor.
 */
@Composable
fun SchemaAreaScreen(
    sync: YamlModelSync<SchemaEditorState, SchemaMode>,
    toYaml: (SchemaEditorState) -> String,
    onSchemaYamlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onInspectField: ((path: String) -> Unit)? = null,
    selectedFieldPath: String? = null,
    readBy: Map<String, Int> = emptyMap(),
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
    SchemaEditorPanel(
        sync = sync,
        toYaml = toYaml,
        onYamlChange = onSchemaYamlChange,
        modifier = modifier,
        onInspectField = onInspectField,
        selectedFieldPath = selectedFieldPath,
        readBy = readBy,
        onMessage = onMessage,
        yamlEditor = yamlEditor,
    )
}
