package ui.manifest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ui.TextSecondary
import ui.components.SectionTitle
import ui.components.ToolbarButton
import ui.editor.YamlEditorPane
import ui.manifest.canvas.ManifestCanvas
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
@Suppress("LongParameterList")
fun ManifestEditorPanel(
    state: ManifestEditorState,
    onStateChange: (ManifestEditorState) -> Unit,
    activeEntryId: String?,
    onSelectEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    fromYaml: (String) -> ManifestEditorState,
    toYaml: (ManifestEditorState) -> String,
    fieldTypes: Map<String, String>? = null,
    /** Which tab is open — see [ui.schema.SchemaEditorPanel]. */
    mode: ManifestMode = ManifestMode.BUILDER,
    /** True while the Inspector is on the manifest itself, which the canvas marks. */
    manifestSelected: Boolean = false,
    onSelectManifest: () -> Unit = {},
    /** The paths are navigation: clicking one opens that area. */
    onOpenSchema: () -> Unit = {},
    onOpenActions: () -> Unit = {},
    /** Whether a manifest-relative path resolves to a file on disk. */
    exists: (String) -> Boolean = { true },
    /** What each rule file of the active entry publishes and reads, by manifest-relative path. */
    variableFlow: Map<String, RuleFileFlow> = emptyMap(),
    modifier: Modifier = Modifier,
    /**
     * The YAML surface for the text tab.
     *
     * A slot for the same reason the Schema and Actions panels have one: the highlighter and the
     * completions are JVM-only, and this file is `commonMain`. Until this existed the manifest — the one
     * file that decides what runs in what order — was the only YAML in the app shown as plain text.
     */
    yamlEditor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit = { value, onValueChange, fieldModifier ->
        OutlinedTextField(
            value = value.text,
            onValueChange = { text -> onValueChange(TextFieldValue(text = text)) },
            modifier = fieldModifier,
            textStyle = MaterialTheme.typography.body2,
        )
    },
) {
    var yamlText by remember { mutableStateOf(toYaml(state)) }
    var yamlError by remember { mutableStateOf<String?>(null) }

    // Arriving on the text tab shows what the manifest is now, not what it was the last time the tab
    // was open. Keyed on the mode rather than done in the tab's click handler, because the tabs are in
    // the area header now and the mode can also change from somewhere else entirely.
    LaunchedEffect(key1 = mode) {
        if (mode != ManifestMode.YAML) return@LaunchedEffect
        yamlText = runCatching { toYaml(state) }.getOrNull() ?: yamlText
        yamlError = null
    }

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
        when (mode) {
            ManifestMode.BUILDER -> BuilderModeBody(
                state = state,
                activeEntryId = activeEntryId,
                manifestSelected = manifestSelected,
                fieldTypes = fieldTypes,
                onSelectManifest = onSelectManifest,
                onSelectEntry = onSelectEntry,
                onAddEntry = onAddEntry,
                onRemoveEntry = onRemoveEntry,
                onOpenSchema = onOpenSchema,
                onOpenActions = onOpenActions,
                exists = exists,
                variableFlow = variableFlow,
            )

            ManifestMode.YAML -> YamlManifestEditor(
                yaml = yamlText,
                error = yamlError,
                onYamlChange = { newText ->
                    yamlText = newText
                    yamlError = null
                },
                yamlEditor = yamlEditor,
            )
        }
    }
}

private const val YAML_PARSE_DELAY_MS = 500L

/** The Builder tab: the canvas, and the two gestures that are about the entry list rather than an entry. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun BuilderModeBody(
    state: ManifestEditorState,
    activeEntryId: String?,
    manifestSelected: Boolean,
    fieldTypes: Map<String, String>?,
    onSelectManifest: () -> Unit,
    onSelectEntry: (String) -> Unit,
    onAddEntry: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onOpenSchema: () -> Unit,
    onOpenActions: () -> Unit,
    exists: (String) -> Boolean,
    variableFlow: Map<String, RuleFileFlow>,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        ManifestCanvas(
            state = state,
            modifier = Modifier.fillMaxWidth().weight(weight = 1f),
            activeEntryId = activeEntryId,
            selected = manifestSelected,
            onSelectManifest = onSelectManifest,
            onSelectEntry = onSelectEntry,
            onOpenSchema = onOpenSchema,
            onOpenActions = onOpenActions,
            exists = exists,
            fieldTypes = fieldTypes,
            variableFlow = variableFlow,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            ToolbarButton(label = "+ Add entry", onClick = onAddEntry)
            if (state.entries.size > 1 && activeEntryId != null) {
                ToolbarButton(label = "Remove entry…", onClick = { onRemoveEntry(activeEntryId) })
            }
        }
    }
}

/** The picker's stand-in for an absent scope, since a dropdown cannot offer emptiness. */
internal const val SCOPE_NONE: String = "(none — run once per document)"

/**
 * What the picker offers, given what the schema declares.
 *
 * A scope the schema does not declare is deliberately *not* added: `DropdownSelector` prepends an
 * off-list value to its own menu and marks it, which is the whole point here — the bad value stays
 * visible and selectable instead of being silently swapped for something legal.
 *
 * The exception is having no schema at all, where there is nothing to check the name against. The
 * value the manifest already carries is offered as itself rather than accused of being undeclared.
 */
private fun scopeOptions(scope: String, fieldTypes: Map<String, String>?): List<String> = buildList {
    add(SCOPE_NONE)
    addAll(collectionNames(fieldTypes = fieldTypes))
    if (fieldTypes == null && scope.isNotBlank()) add(scope)
}

/**
 * Why the scope cannot work, when it cannot.
 *
 * The picker's own marker says *that* a value is off-list; this says *why*, in the engine's words.
 * Both are still needed once the field is a dropdown, because a manifest can arrive from disk or
 * from the YAML tab carrying a scope the picker would never have offered.
 */
@Composable
private fun ScopeFieldNote(issue: String?) {
    if (issue == null) return
    Text(
        text = issue,
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.error,
    )
}

@Composable
private fun YamlManifestEditor(
    yaml: String,
    error: String?,
    onYamlChange: (String) -> Unit,
    yamlEditor: @Composable (
        value: TextFieldValue,
        onValueChange: (TextFieldValue) -> Unit,
        modifier: Modifier,
    ) -> Unit,
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
        YamlEditorPane(
            yaml = yaml,
            onYamlChange = onYamlChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            editor = yamlEditor,
        )
    }
}
