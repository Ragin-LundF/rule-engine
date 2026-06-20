package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import ui.manifest.ManifestEditorPanel
import ui.manifest.ManifestEditorState

/**
 * Manifest builder area.
 */
@Composable
fun ManifestAreaScreen(
    manifestYaml: String,
    fromYaml: (String) -> ManifestEditorState,
    toYaml: (ManifestEditorState) -> String,
    onManifestYamlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManifestEditorPanel(
        yaml = manifestYaml,
        fromYaml = fromYaml,
        toYaml = toYaml,
        onYamlChange = onManifestYamlChange,
        initialMode = ManifestMode.BUILDER,
        modifier = modifier.fillMaxSize(),
    )
}
