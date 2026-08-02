package ui.manifest

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.components.ModeTabs
import ui.workbench.model.mode.ManifestMode

/**
 * Tab switcher for the Manifest editor area: Builder / YAML / Checks.
 */
@Composable
fun ManifestModeTabs(
    current: ManifestMode,
    onSelect: (ManifestMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModeTabs(
        modes = ManifestMode.entries,
        current = current,
        label = { it.displayName },
        onSelect = onSelect,
        modifier = modifier,
    )
}

private val ManifestMode.displayName: String
    get() = when (this) {
        ManifestMode.BUILDER -> "Builder"
        ManifestMode.YAML -> "YAML"
        ManifestMode.CHECKS -> "Checks"
    }
