package ui.manifest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ui.TextSecondary
import ui.components.PathListEditor
import ui.components.SectionTitle
import ui.workbench.ManifestMode

/**
 * Manifest builder panel with Builder / YAML / Checks tabs.
 */
@Composable
fun ManifestEditorPanel(
    yaml: String,
    fromYaml: (String) -> ManifestEditorState,
    toYaml: (ManifestEditorState) -> String,
    onYamlChange: (String) -> Unit,
    initialMode: ManifestMode = ManifestMode.BUILDER,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var editorState by remember { mutableStateOf(fromYaml(yaml)) }
    var yamlText by remember { mutableStateOf(yaml) }
    var yamlError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = yaml) {
        if (yaml != yamlText) {
            yamlText = yaml
            editorState = fromYaml(yaml)
            yamlError = null
        }
    }

    LaunchedEffect(key1 = editorState, key2 = mode) {
        if (mode == ManifestMode.YAML) return@LaunchedEffect
        val generated = runCatching { toYaml(editorState) }.getOrNull() ?: return@LaunchedEffect
        if (generated != yamlText) {
            yamlText = generated
            onYamlChange(yamlText)
        }
    }

    LaunchedEffect(key1 = yamlText, key2 = mode) {
        if (mode != ManifestMode.YAML) return@LaunchedEffect
        delay(timeMillis = 500)
        val parsed = runCatching { fromYaml(yamlText) }.getOrNull()
        if (parsed != null && !parsed.isReadOnly) {
            editorState = parsed
            yamlError = null
        } else {
            yamlError = "Invalid YAML: could not parse manifest"
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManifestModeTabs(
            current = mode,
            onSelect = { newMode ->
                if (newMode == ManifestMode.YAML && mode != ManifestMode.YAML) {
                    yamlText = runCatching { toYaml(editorState) }.getOrNull() ?: yamlText
                    onYamlChange(yamlText)
                }
                if (newMode != ManifestMode.YAML && mode == ManifestMode.YAML) {
                    val generated = runCatching { toYaml(editorState) }.getOrNull()
                    if (generated != null) {
                        yamlText = generated
                        onYamlChange(yamlText)
                    }
                }
                mode = newMode
            },
        )

        when (mode) {
            ManifestMode.BUILDER -> VisualManifestEditor(
                state = editorState,
                onStateChange = { editorState = it },
            )
            ManifestMode.YAML -> YamlManifestEditor(
                yaml = yamlText,
                error = yamlError,
                onYamlChange = { newText ->
                    yamlText = newText
                    yamlError = null
                },
            )
            ManifestMode.CHECKS -> ManifestChecksPanel(state = editorState)
        }
    }
}

@Composable
private fun VisualManifestEditor(
    state: ManifestEditorState,
    onStateChange: (ManifestEditorState) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(text = "MANIFEST")
        OutlinedTextField(
            value = state.name,
            onValueChange = { onStateChange(state.copy(name = it)) },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        val entry = state.entries.firstOrNull() ?: EditableManifestEntry()
        ManifestEntryCard(
            entry = entry,
            onEntryChange = { updated ->
                onStateChange(state.copy(entries = listOf(updated)))
            },
        )
    }
}

@Composable
private fun ManifestEntryCard(
    entry: EditableManifestEntry,
    onEntryChange: (EditableManifestEntry) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = entry.schemaPath,
            onValueChange = { onEntryChange(entry.copy(schemaPath = it)) },
            label = { Text("Field schema file") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = entry.actionsPath,
            onValueChange = { onEntryChange(entry.copy(actionsPath = it)) },
            label = { Text("Action schema file") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SectionTitle(text = "RULE FILES")
        PathListEditor(
            paths = entry.rulePaths,
            onPathsChange = { onEntryChange(entry.copy(rulePaths = it)) },
            label = "Rule file",
        )
    }
}

@Composable
private fun YamlManifestEditor(
    yaml: String,
    error: String?,
    onYamlChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(text = "YAML")
            Text(
                text = "Auto-reloads on valid YAML",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.error,
            )
        }
        OutlinedTextField(
            value = yaml,
            onValueChange = onYamlChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.body2,
        )
    }
}

@Composable
private fun ManifestChecksPanel(state: ManifestEditorState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(text = "CHECKS")
        val issues = buildList {
            if (state.name.isBlank()) add("Manifest name is empty")
            val entry = state.entries.firstOrNull()
            if (entry == null) {
                add("No manifest entry defined")
            } else {
                if (entry.schemaPath.isBlank()) add("Field schema file not set")
                if (entry.actionsPath.isBlank()) add("Action schema file not set")
                if (entry.rulePaths.isEmpty()) add("No rule files configured")
            }
        }
        if (issues.isEmpty()) {
            Text(
                text = "✓ Manifest structure looks valid",
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body2,
            )
        } else {
            issues.forEach { issue ->
                Text(
                    text = "• $issue",
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}
