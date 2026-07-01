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
    val schemaText: MutableState<String> = mutableStateOf(value = "")
    val schemaFieldValue: MutableState<TextFieldValue> = mutableStateOf(value = TextFieldValue(text = ""))
    val ruleValue: MutableState<TextFieldValue> = mutableStateOf(value = TextFieldValue(text = ""))

    // Status
    val status: MutableState<String> = mutableStateOf(value = "Ready")
    val statusKind: MutableState<StatusKind> = mutableStateOf(value = StatusKind.IDLE)

    // Parsed schema/action
    val parsedSchema: MutableState<FieldSchema?> = mutableStateOf(value = null)
    val actionSchemaText: MutableState<String> = mutableStateOf(value = "")
    val actionFieldValue: MutableState<TextFieldValue> = mutableStateOf(value = TextFieldValue(text = ""))
    val parsedActionSchema: MutableState<ActionSchema?> = mutableStateOf(value = null)

    // Manifest
    val manifestText: MutableState<String> = mutableStateOf(value = "")
    val manifestFieldValue: MutableState<TextFieldValue> = mutableStateOf(value = TextFieldValue(text = ""))
    val manifestBaseDir: MutableState<String?> = mutableStateOf(value = null)
    val parsedManifest: MutableState<ProjectManifest?> = mutableStateOf(value = null)
    val selectedManifestEntry: MutableState<String?> = mutableStateOf(value = null)
    val selectedManifestRuleFile: MutableState<String?> = mutableStateOf(value = null)
    val showAllRules: MutableState<Boolean> = mutableStateOf(value = false)
    val allRulesText: MutableState<String> = mutableStateOf(value = "")

    // Diagnostics
    val diagnosticsList: MutableState<List<ValidationDiagnostic>> = mutableStateOf(value = emptyList())
    val diagnosticsText: MutableState<String> = mutableStateOf(value = "")

    // Expand/collapse
    val schemaExpanded: MutableState<Boolean> = mutableStateOf(value = false)
    val actionsExpanded: MutableState<Boolean> = mutableStateOf(value = false)
    val showManifestYaml: MutableState<Boolean> = mutableStateOf(value = false)

    // Editor UX
    val cursorRect: MutableState<Rect> = mutableStateOf(value = Rect.Zero)
    val showAutoComplete: MutableState<Boolean> = mutableStateOf(value = false)
    val autoCompleteIndex: MutableState<Int> = mutableStateOf(value = 0)
    val autoCompleteWord: MutableState<String> = mutableStateOf(value = "")
    val autoCompleteWordStart: MutableState<Int> = mutableStateOf(value = 0)
    val dslContext: MutableState<DslCursorContext> = mutableStateOf(
        value = DslCursorContext(section = DslSection.TOP_LEVEL)
    )
    val splitFraction: MutableState<Float> = mutableStateOf(value = DEFAULT_SPLIT_FRACTION)
    val viewMode: MutableState<ViewMode> = mutableStateOf(value = ViewMode.CODE)
    val showExpandedDiagram: MutableState<Boolean> = mutableStateOf(value = false)

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
        selectedManifestRuleFile.value = null
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() } ?: run {
            reportManifestPathIssue(message = "Manifest base directory is not set")
            return
        }

        val validationMessage = validateManifestEntryPaths(baseDir = base, entry = entry)
        if (validationMessage != null) {
            reportManifestPathIssue(message = validationMessage)
            return
        }

        if (!loadManifestSchema(baseDir = base, entry = entry)) {
            return
        }
        if (!loadManifestActions(baseDir = base, entry = entry)) {
            return
        }
        val loadedRules = loadManifestRules(baseDir = base, entry = entry) ?: return

        diagnosticsText.value = ""
        diagnosticsList.value = emptyList()
        setStatus(
            msg = "Loaded '${entry.id}'" +
                    (if (entry.schema != null) ", schema" else "") +
                    (if (entry.actions != null) ", actions" else "") +
                    (if (entry.rules.isNotEmpty()) ", ${entry.rules.size} rule file(s) — showing first" else ""),
            kind = StatusKind.SUCCESS,
        )
    }

    private fun loadManifestSchema(baseDir: Path, entry: ManifestEntry): Boolean {
        val relativePath = entry.schema ?: return true
        return runCatching {
            val path = resolveManifestPathOrThrow(
                baseDir = baseDir,
                relativePath = relativePath,
                label = "schema",
            )
            val content = Files.readString(path)
            schemaText.value = content
            schemaFieldValue.value = TextFieldValue(text = content)
            parsedSchema.value = runCatching {
                FieldSchemaLoader.loadFromString(
                    content = content,
                    nameHint = path.fileName.toString(),
                )
            }.getOrNull()
            true
        }.getOrElse { ex ->
            reportManifestPathIssue(message = "Failed to load manifest schema: ${ex.message}")
            false
        }
    }

    private fun loadManifestActions(baseDir: Path, entry: ManifestEntry): Boolean {
        val relativePath = entry.actions ?: return true
        return runCatching {
            val path = resolveManifestPathOrThrow(
                baseDir = baseDir,
                relativePath = relativePath,
                label = "actions",
            )
            val content = Files.readString(path)
            actionSchemaText.value = content
            actionFieldValue.value = TextFieldValue(text = content)
            parsedActionSchema.value = runCatching {
                ActionSchemaLoader.loadFromString(content = content)
            }.getOrNull()
            true
        }.getOrElse { ex ->
            reportManifestPathIssue(message = "Failed to load manifest actions: ${ex.message}")
            false
        }
    }

    private fun loadManifestRules(baseDir: Path, entry: ManifestEntry): Int? {
        if (entry.rules.isEmpty()) return 0
        val firstRelPath = entry.rules.first()
        showAllRules.value = false
        return runCatching {
            val path = resolveManifestPathOrThrow(baseDir = baseDir, relativePath = firstRelPath, label = "rule")
            val content = Files.readString(path)
            ruleValue.value = TextFieldValue(text = content)
            selectedManifestRuleFile.value = firstRelPath
            diagnosticsText.value = ""
            diagnosticsList.value = emptyList()
            1
        }.getOrElse { ex ->
            reportManifestPathIssue(message = "Failed to load manifest rule: ${ex.message}")
            null
        }
    }

    /** Load all rule files for the current manifest entry and expose them via [allRulesText]. */
    fun loadAllRuleFilesForCurrentEntry() {
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() } ?: return
        val entry = parsedManifest.value?.entries?.find { it.id == selectedManifestEntry.value } ?: return
        val combined = entry.rules.mapNotNull { relativePath ->
            runCatching {
                val path = resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule")
                Files.readString(path)
            }.getOrNull()
        }.joinToString(separator = "\n\n")
        allRulesText.value = combined
        showAllRules.value = true
    }

    /** Load a single rule file from the current manifest entry into the editor. */
    fun loadSingleManifestRuleFile(relativePath: String) {
        showAllRules.value = false
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() } ?: run {
            reportManifestPathIssue(message = "Manifest base directory is not set")
            return
        }
        runCatching {
            val path = resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule")
            val content = Files.readString(path)
            ruleValue.value = TextFieldValue(text = content)
            selectedManifestRuleFile.value = relativePath
            diagnosticsText.value = ""
            diagnosticsList.value = emptyList()
            setStatus(msg = "Loaded ${path.fileName}", kind = StatusKind.SUCCESS)
        }.onFailure { ex ->
            reportManifestPathIssue(message = "Failed to load rule file: ${ex.message}")
        }
    }

    /**
     * Save the current editor content back to the manifest-referenced rule file.
     * Returns true if the save was handled (manifest-backed), false if the caller
     * should fall back to a generic save dialog.
     */
    fun saveCurrentManifestRuleFile(): Boolean {
        val relativePath = selectedManifestRuleFile.value ?: return false
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() } ?: return false
        return runCatching {
            val path = resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule")
            Files.writeString(path, ruleValue.value.text)
            setStatus(msg = "Saved ${path.fileName}", kind = StatusKind.SUCCESS)
            true
        }.getOrElse { ex ->
            reportManifestPathIssue(message = "Failed to save rule file: ${ex.message}")
            false
        }
    }

    private fun validateManifestEntryPaths(baseDir: Path, entry: ManifestEntry): String? {
        entry.schema?.let {
            val rejected = ManifestPathResolver.resolveWithinBase(
                baseDir = baseDir,
                relativePath = it,
                label = "schema",
            )
            if (rejected is ManifestPathResolution.Rejected) return rejected.message
        }
        entry.actions?.let {
            val rejected = ManifestPathResolver.resolveWithinBase(
                baseDir = baseDir,
                relativePath = it,
                label = "actions",
            )
            if (rejected is ManifestPathResolution.Rejected) return rejected.message
        }
        for (relativePath in entry.rules) {
            val rejected = ManifestPathResolver.resolveWithinBase(
                baseDir = baseDir,
                relativePath = relativePath,
                label = "rule",
            )
            if (rejected is ManifestPathResolution.Rejected) return rejected.message
        }
        return null
    }

    private fun resolveManifestPathOrThrow(
        baseDir: Path,
        relativePath: String,
        label: String,
    ): Path {
        return when (
            val resolution = ManifestPathResolver.resolveWithinBase(
                baseDir = baseDir,
                relativePath = relativePath,
                label = label,
            )
        ) {
            is ManifestPathResolution.Accepted -> resolution.path
            is ManifestPathResolution.Rejected -> throw IllegalArgumentException(resolution.message)
        }
    }

    private fun reportManifestPathIssue(message: String) {
        diagnosticsText.value = message
        diagnosticsList.value = emptyList()
        setStatus(msg = message, kind = StatusKind.ERROR)
    }
}
