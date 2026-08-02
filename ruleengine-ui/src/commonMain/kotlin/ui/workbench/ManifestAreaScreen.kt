package ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.manifest.ManifestEditorPanel
import ui.manifest.model.ManifestEditorState
import ui.workbench.model.mode.ManifestMode

/**
 * Manifest builder area.
 */
@Composable
@Suppress("LongParameterList")
fun ManifestAreaScreen(
    state: ManifestEditorState,
    onStateChange: (ManifestEditorState) -> Unit,
    activeEntryId: String?,
    onSelectEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    fromYaml: (String) -> ManifestEditorState,
    toYaml: (ManifestEditorState) -> String,
    fieldTypes: Map<String, String>? = null,
    modifier: Modifier = Modifier,
) {
    ManifestEditorPanel(
        state = state,
        onStateChange = onStateChange,
        activeEntryId = activeEntryId,
        onSelectEntry = onSelectEntry,
        onAddEntry = onAddEntry,
        onRemoveEntry = onRemoveEntry,
        fromYaml = fromYaml,
        toYaml = toYaml,
        fieldTypes = fieldTypes,
        initialMode = ManifestMode.BUILDER,
        modifier = modifier.fillMaxSize(),
    )
}
