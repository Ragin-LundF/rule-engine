package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ui.schema.SchemaEditorPanel
import ui.schema.SchemaEditorState

/**
 * Field Schema editor area.
 *
 * Replaces the earlier placeholder with a real Visual/YAML/Usages editor.
 */
@Composable
fun SchemaAreaScreen(
    schemaYaml: String,
    fromYaml: (String) -> SchemaEditorState,
    toYaml: (SchemaEditorState) -> String,
    onSchemaYamlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
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
        yaml = schemaYaml,
        fromYaml = fromYaml,
        toYaml = toYaml,
        onYamlChange = onSchemaYamlChange,
        modifier = modifier,
        yamlEditor = yamlEditor,
    )
}
