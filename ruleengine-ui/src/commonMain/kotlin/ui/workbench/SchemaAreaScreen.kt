package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
) {
    SchemaEditorPanel(
        yaml = schemaYaml,
        fromYaml = fromYaml,
        toYaml = toYaml,
        onYamlChange = onSchemaYamlChange,
        modifier = modifier,
    )
}
