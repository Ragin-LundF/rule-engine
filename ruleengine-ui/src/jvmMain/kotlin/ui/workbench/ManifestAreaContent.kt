package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.editor.rules.RuleEditorState
import ui.manifest.ManifestYamlBridge
import ui.project.ProjectWorkspace
import ui.project.toEditorState

/**
 * The Manifest area.
 *
 * The session is the manifest: edits here go straight onto it rather than into a text buffer the
 * saver would regenerate over. The manifest text is only the fallback for when no project is open.
 */
@Suppress("FunctionNaming")
@Composable
fun ManifestAreaContent(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    modifier: Modifier = Modifier,
) {
    ManifestAreaScreen(
        state = workspace.session.value?.toEditorState()
            ?: ManifestYamlBridge.fromYaml(yaml = state.manifestText.value),
        onStateChange = { edited -> workspace.applyManifestEditorState(edited = edited) },
        activeEntryId = workspace.session.value?.activeEntryId,
        onSelectEntry = { entryId -> workspace.selectEntry(entryId = entryId) },
        onAddEntry = { workspace.addEntry(entryId = workspace.suggestEntryId()) },
        onRemoveEntry = { entryId -> workspace.requestRemoveEntry(entryId = entryId) },
        fromYaml = { yaml ->
            ManifestYamlBridge.fromYaml(yaml = yaml)
        },
        toYaml = { editorState ->
            ManifestYamlBridge.toYaml(state = editorState)
        },
        modifier = modifier,
    )
}
