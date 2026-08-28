package ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import ui.manifest.ManifestEditorPanel
import ui.manifest.RuleFileFlow
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
    mode: ManifestMode = ManifestMode.BUILDER,
    manifestSelected: Boolean = false,
    onSelectManifest: () -> Unit = {},
    onOpenSchema: () -> Unit = {},
    onOpenActions: () -> Unit = {},
    isLoaded: (String) -> Boolean = { true },
    /** What each rule file of the active entry publishes and reads, by manifest-relative path. */
    variableFlow: Map<String, RuleFileFlow> = emptyMap(),
    modifier: Modifier = Modifier,
    yamlEditor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit = { value, onValueChange, fieldModifier ->
        androidx.compose.material.OutlinedTextField(
            value = value.text,
            onValueChange = { text -> onValueChange(TextFieldValue(text = text)) },
            modifier = fieldModifier,
            textStyle = androidx.compose.material.MaterialTheme.typography.body2,
        )
    },
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
        mode = mode,
        manifestSelected = manifestSelected,
        onSelectManifest = onSelectManifest,
        onOpenSchema = onOpenSchema,
        onOpenActions = onOpenActions,
        exists = isLoaded,
        variableFlow = variableFlow,
        modifier = modifier.fillMaxSize(),
        yamlEditor = yamlEditor,
    )
}
