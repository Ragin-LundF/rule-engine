package ui.manifest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary
import ui.components.PathListEditor
import ui.components.SectionTitle
import ui.components.StatusBadge
import ui.components.ToolbarButton
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.workbench.model.mode.ManifestMode

/**
 * Manifest builder panel with Builder / YAML / Checks tabs.
 *
 * The state is owned by the caller rather than by this panel: it is the same manifest the project
 * saver writes, so a private copy here would be a second version of the truth that quietly loses to
 * the session on the next save.
 */
@Composable
fun ManifestEditorPanel(
    state: ManifestEditorState,
    onStateChange: (ManifestEditorState) -> Unit,
    activeEntryId: String?,
    onSelectEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    fromYaml: (String) -> ManifestEditorState,
    toYaml: (ManifestEditorState) -> String,
    initialMode: ManifestMode = ManifestMode.BUILDER,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var yamlText by remember { mutableStateOf(toYaml(state)) }
    var yamlError by remember { mutableStateOf<String?>(null) }

    // Typing YAML parses on a pause rather than per keystroke: half-finished YAML is unparseable
    // almost all of the time, and reporting that on every character makes the tab unusable.
    LaunchedEffect(key1 = yamlText, key2 = mode) {
        if (mode != ManifestMode.YAML) return@LaunchedEffect
        delay(timeMillis = YAML_PARSE_DELAY_MS)
        val parsed = runCatching { fromYaml(yamlText) }.getOrNull()
        if (parsed == null || parsed.isReadOnly) {
            yamlError = "Invalid YAML: could not parse manifest"
            return@LaunchedEffect
        }
        yamlError = null
        if (parsed != state) onStateChange(parsed)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ManifestModeTabs(
            current = mode,
            onSelect = { newMode ->
                // Entering the YAML tab shows what the manifest currently is, not what it was the
                // last time the tab was open.
                if (newMode == ManifestMode.YAML) {
                    yamlText = runCatching { toYaml(state) }.getOrNull() ?: yamlText
                    yamlError = null
                }
                mode = newMode
            },
        )

        when (mode) {
            ManifestMode.BUILDER -> VisualManifestEditor(
                state = state,
                onStateChange = onStateChange,
                activeEntryId = activeEntryId,
                onSelectEntry = onSelectEntry,
                onAddEntry = onAddEntry,
                onRemoveEntry = onRemoveEntry,
            )

            ManifestMode.YAML -> YamlManifestEditor(
                yaml = yamlText,
                error = yamlError,
                onYamlChange = { newText ->
                    yamlText = newText
                    yamlError = null
                },
            )

            ManifestMode.CHECKS -> ManifestChecksPanel(state = state)
        }
    }
}

private const val YAML_PARSE_DELAY_MS = 500L

@Composable
private fun VisualManifestEditor(
    state: ManifestEditorState,
    onStateChange: (ManifestEditorState) -> Unit,
    activeEntryId: String?,
    onSelectEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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

        SectionTitle(text = "ENTRIES")
        state.entries.forEachIndexed { index, entry ->
            ManifestEntryCard(
                entry = entry,
                index = index,
                isActive = entry.id == activeEntryId,
                hasSiblings = state.entries.size > 1,
                onSelect = { onSelectEntry(entry.id) },
                onRemove = { onRemoveEntry(entry.id) },
                onEntryChange = { updated ->
                    onStateChange(
                        state.copy(
                            entries = state.entries.toMutableList().also { it[index] = updated },
                        ),
                    )
                },
            )
        }

        ToolbarButton(label = "+ Add entry", onClick = onAddEntry)
    }
}

@Composable
private fun ManifestEntryCard(
    entry: EditableManifestEntry,
    index: Int,
    isActive: Boolean,
    hasSiblings: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onEntryChange: (EditableManifestEntry) -> Unit,
) {
    // The id is edited locally and only committed once it is non-blank: pushing an empty id upwards
    // would make the entry vanish from the manifest between two keystrokes.
    var idDraft by remember(index) { mutableStateOf(entry.id) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = if (isActive) PrimaryBlue else BorderColor,
                shape = RoundedCornerShape(size = 8.dp),
            )
            .padding(all = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            if (isActive) StatusBadge(label = "EDITING", color = PrimaryBlue)
            Spacer(modifier = Modifier.weight(weight = 1f))
            // Both controls need a sibling to make sense: with a single entry there is nothing to
            // switch to and nothing that may be removed. `!isActive` alone would not do — with no
            // project open there is no active entry, so the sole card would still offer the switch.
            if (hasSiblings && !isActive) ToolbarButton(label = "Switch to this entry", onClick = onSelect)
            if (hasSiblings) ToolbarButton(label = "Remove…", onClick = onRemove)
        }

        OutlinedTextField(
            value = idDraft,
            onValueChange = { newId ->
                idDraft = newId
                if (newId.isNotBlank()) onEntryChange(entry.copy(id = newId.trim()))
            },
            label = { Text("Name (entry id)") },
            isError = idDraft.isBlank(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        ManifestEntryFields(entry = entry, onEntryChange = onEntryChange)
        SectionTitle(text = "RULE FILES")
        PathListEditor(
            paths = entry.rulePaths,
            onPathsChange = { onEntryChange(entry.copy(rulePaths = it)) },
            label = "Rule file",
        )
    }
}

/** The entry's settings other than its id and its rule files. */
@Composable
private fun ManifestEntryFields(
    entry: EditableManifestEntry,
    onEntryChange: (EditableManifestEntry) -> Unit,
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
    // Left blank the rules run once for the whole document, which is the default and what every
    // manifest written before this setting existed means.
    OutlinedTextField(
        value = entry.scope,
        onValueChange = { onEntryChange(entry.copy(scope = it)) },
        label = { Text("Scope — run once per collection member (optional)") },
        placeholder = { Text("collection field, e.g. accounts") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
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
        val issues = manifestIssues(state = state)
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

/**
 * What would stop this manifest from loading, plus the softer omissions.
 *
 * Duplicate entry ids are the one that matters most: the engine rejects the manifest outright, and
 * the id is otherwise invisible enough that two entries can end up sharing one by accident.
 */
private fun manifestIssues(state: ManifestEditorState): List<String> {
    return buildList {
        if (state.name.isBlank()) add("Manifest name is empty")
        if (state.entries.isEmpty()) {
            add("No manifest entry defined")
            return@buildList
        }

        state.entries
            .groupBy { it.id }
            .filter { (id, entries) -> id.isNotBlank() && entries.size > 1 }
            .forEach { (id, _) -> add("Entry id \"$id\" is used more than once") }

        state.entries.forEachIndexed { index, entry ->
            val label = entry.id.ifBlank { "entry ${index + 1}" }
            if (entry.id.isBlank()) add("$label has no id")
            if (entry.schemaPath.isBlank()) add("$label: field schema file not set")
            if (entry.actionsPath.isBlank()) add("$label: action schema file not set")
            if (entry.rulePaths.isEmpty()) add("$label: no rule files configured")
        }
    }
}
