package ui.editor.rules

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ruleengine.core.domain.ActionSchema
import ruleengine.core.domain.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ProjectManifest
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.DslCursorContext
import ui.DslSection
import ui.autocompletion.CompletionItem
import java.nio.file.Files
import java.nio.file.Path

/**
 * Centralized state holder for the Rule Editor.
 * All UI state is stored here as MutableState so it can be passed around
 * or hoisted into other components easily.
 */
class RuleEditorState(
    val scope: CoroutineScope,
) {
    companion object {
        /** Default split fraction between the left schema panel and the right editor panel. */
        private const val DEFAULT_SPLIT_FRACTION = 0.33f
    }
    // Text fields
    val schemaText: MutableState<String> = mutableStateOf("")
    val schemaFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))
    val ruleValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))

    // Status
    val status: MutableState<String> = mutableStateOf("Ready")
    val statusKind: MutableState<StatusKind> = mutableStateOf(StatusKind.IDLE)

    // Parsed schema/action
    val parsedSchema: MutableState<FieldSchema?> = mutableStateOf(null)
    val actionSchemaText: MutableState<String> = mutableStateOf("")
    val actionFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))
    val parsedActionSchema: MutableState<ActionSchema?> = mutableStateOf(null)

    // Manifest
    val manifestText: MutableState<String> = mutableStateOf("")
    val manifestFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(text = ""))
    val manifestBaseDir: MutableState<String?> = mutableStateOf(null)
    val parsedManifest: MutableState<ProjectManifest?> = mutableStateOf(null)
    val selectedManifestEntry: MutableState<String?> = mutableStateOf(null)

    // Diagnostics
    val diagnosticsList: MutableState<List<ValidationDiagnostic>> = mutableStateOf(emptyList())
    val diagnosticsText: MutableState<String> = mutableStateOf("")

    // Expand/collapse
    val schemaExpanded: MutableState<Boolean> = mutableStateOf(false)
    val actionsExpanded: MutableState<Boolean> = mutableStateOf(false)
    val showManifestYaml: MutableState<Boolean> = mutableStateOf(false)

    // Editor UX
    val cursorRect: MutableState<Rect> = mutableStateOf(Rect.Zero)
    val showAutoComplete: MutableState<Boolean> = mutableStateOf(false)
    val autoCompleteIndex: MutableState<Int> = mutableStateOf(0)
    val autoCompleteWord: MutableState<String> = mutableStateOf("")
    val autoCompleteWordStart: MutableState<Int> = mutableStateOf(0)
    val dslContext: MutableState<DslCursorContext> = mutableStateOf(DslCursorContext(section = DslSection.TOP_LEVEL))
    val splitFraction: MutableState<Float> = mutableStateOf(DEFAULT_SPLIT_FRACTION)
    val viewMode: MutableState<ViewMode> = mutableStateOf(ViewMode.CODE)
    val showExpandedDiagram: MutableState<Boolean> = mutableStateOf(false)

    // Parsed rules for diagram are derived in UI; kept as helper nullable here if needed

    // Helper methods
    fun setStatus(msg: String, kind: StatusKind) {
        status.value = msg
        statusKind.value = kind
    }

    /** Accept an autocomplete suggestion and insert it at the current cursor position. */
    fun acceptSuggestion(item: CompletionItem) {
        val cursor = ruleValue.value.selection.start
        val newText = ruleValue.value.text.substring(startIndex = 0, endIndex = autoCompleteWordStart.value) +
                item.insertText +
                ruleValue.value.text.substring(startIndex = cursor)
        val newPos = autoCompleteWordStart.value + item.insertText.length
        ruleValue.value = TextFieldValue(text = newText, selection = TextRange(index = newPos))
        showAutoComplete.value = false
    }

    /** Load all files referenced by a manifest entry into the editor state. */
    fun loadManifestEntry(entry: ManifestEntry) {
        selectedManifestEntry.value = entry.id
        val base = manifestBaseDir.value ?: return
        var loadedRules = 0

        // Load schema if referenced
        entry.schema?.let { sp ->
            runCatching {
                val p = Path.of(base, sp)
                val c = Files.readString(p)
                schemaText.value = c
                schemaFieldValue.value = TextFieldValue(text = c)
                parsedSchema.value = runCatching {
                    FieldSchemaLoader.loadFromString(content = c, nameHint = p.fileName.toString())
                }.getOrNull()
            }
        }
        // Load actions if referenced
        entry.actions?.let { ap ->
            runCatching {
                val p = Path.of(base, ap)
                val c = Files.readString(p)
                actionSchemaText.value = c
                actionFieldValue.value = TextFieldValue(text = c)
                parsedActionSchema.value = runCatching {
                    ActionSchemaLoader.loadFromString(content = c)
                }.getOrNull()
            }
        }
        // Load and concatenate all rule files
        if (entry.rules.isNotEmpty()) {
            val combined = buildString {
                entry.rules.forEachIndexed { idx, rp ->
                    runCatching {
                        val p = Path.of(base, rp)
                        val c = Files.readString(p)
                        if (idx > 0) append("\n\n")
                        append("# --- ${p.fileName} ---\n")
                        append(c)
                        loadedRules++
                    }
                }
            }
            if (combined.isNotBlank()) ruleValue.value = TextFieldValue(text = combined)
        }
        setStatus(
            msg = "Loaded '${entry.id}'" +
                    (if (entry.schema != null) ", schema" else "") +
                    (if (entry.actions != null) ", actions" else "") +
                    (if (loadedRules > 0) ", $loadedRules rule file(s)" else ""),
            kind = StatusKind.SUCCESS,
        )
    }
}
