package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.actions.ActionEditorPanel
import ui.actions.ActionEditorState

/**
 * Action Schema editor area.
 *
 * Replaces the placeholder with a real Visual/YAML/Usages editor.
 */
@Composable
fun ActionsAreaScreen(
    actionsYaml: String,
    fromYaml: (String) -> ActionEditorState,
    toYaml: (ActionEditorState) -> String,
    onActionsYamlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionEditorPanel(
        yaml = actionsYaml,
        fromYaml = fromYaml,
        toYaml = toYaml,
        onYamlChange = onActionsYamlChange,
        modifier = modifier,
    )
}
