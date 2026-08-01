package ui.editor.rules

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ruleengine.core.domain.dto.ActionSchema
import ruleengine.core.domain.dto.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestEntry
import ruleengine.manifest.ManifestPathResolution
import ruleengine.manifest.ManifestPathResolver
import ruleengine.manifest.ProjectManifest
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.DslCursorContext
import ui.DslSection
import ui.autocompletion.CompletionItem
import ui.diagrams.DiagramViewKind
import ui.diagrams.model.RuleSource
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

    /**
     * The current entry's rule files parsed one by one, keeping the file each rule came from.
     *
     * [allRulesText] cannot answer that: it is the files joined together, and the join is exactly
     * where the provenance is lost. The manifest run diagram needs it to label the file bands that
     * show grouping into files is an organisation choice and not a runtime boundary.
     */
    val entryRuleSources: MutableState<List<RuleSource>> = mutableStateOf(value = emptyList())

    // Diagnostics
    val diagnosticsList: MutableState<List<ValidationDiagnostic>> = mutableStateOf(value = emptyList())
    val diagnosticsText: MutableState<String> = mutableStateOf(value = "")

    // Expand/collapse
    val schemaExpanded: MutableState<Boolean> = mutableStateOf(value = false)
    val actionsExpanded: MutableState<Boolean> = mutableStateOf(value = false)
    val showManifestYaml: MutableState<Boolean> = mutableStateOf(value = false)

    /**
     * Whether the diagnostics list is shown. Starts collapsed so it does not compete with the center
     * panel for height on first launch; the severity badges stay visible in the header either way, so a
     * collapsed panel never hides that there are errors.
     */
    val diagnosticsExpanded: MutableState<Boolean> = mutableStateOf(value = false)

    /** Whether the right inspector/simulate panel is expanded to its full width or collapsed to a strip. */
    val rightPanelExpanded: MutableState<Boolean> = mutableStateOf(value = true)

    /** Whether the Builder rule tree is expanded; collapsing it hands its width to the rule editor. */
    val ruleTreeExpanded: MutableState<Boolean> = mutableStateOf(value = true)

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

    /**
     * Which diagram Diagram mode draws. Held here rather than inside the diagram because the
     * selector lives in the center panel's toolbar and the canvas lives further down the tree.
     */
    val diagramView: MutableState<DiagramViewKind> = mutableStateOf(value = DiagramViewKind.TREE)

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
        // Belongs to the entry being left; keeping it would draw the previous entry's file bands.
        entryRuleSources.value = emptyList()
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

    /**
     * Load all rule files for the current manifest entry and expose them via [allRulesText] and
     * [entryRuleSources].
     *
     * Both come out of the same read so they can never disagree about which files were loaded: the
     * concatenated text is what the code editor and the tester consume, while the per-file parse is
     * what the manifest run diagram needs, since joining the files first would lose which file a rule
     * was written in.
     */
    fun loadAllRuleFilesForCurrentEntry() {
        val loaded = readCurrentEntryRuleFiles()
        allRulesText.value = loaded.joinToString(separator = "\n\n") { (_, content) -> content }
        entryRuleSources.value = parseRuleSources(loaded = loaded)
        showAllRules.value = true
    }

    /**
     * The current entry's rule files, parsed per file, without changing what the editor is showing.
     *
     * [entryRuleSources] only holds them once the user has switched to "All files", but an
     * entry-wide action such as the rule-overview export needs them whichever single file happens to
     * be open. Reading here rather than reusing that state keeps the export from silently covering
     * only part of the entry.
     *
     * Reads from disk, so unsaved edits in the open buffer are not included — the caller is expected
     * to say so rather than to merge them in, which would export a mixture of saved and unsaved
     * rules with no way for the reader to tell which was which.
     */
    fun parsedRuleFilesForCurrentEntry(): List<RuleSource> {
        return parseRuleSources(loaded = readCurrentEntryRuleFiles())
    }

    /** True when the open rule file differs from what is on disk. */
    fun currentRuleFileHasUnsavedChanges(): Boolean {
        val relativePath = selectedManifestRuleFile.value ?: return false
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() } ?: return false

        return runCatching {
            val path = resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule")
            Files.readString(path) != ruleValue.value.text
        }.getOrDefault(defaultValue = false)
    }

    private fun readCurrentEntryRuleFiles(): List<Pair<String, String>> {
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: return emptyList()
        val entry = parsedManifest.value?.entries?.find { it.id == selectedManifestEntry.value }
            ?: return emptyList()

        return entry.rules.mapNotNull { relativePath ->
            runCatching {
                val path = resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule")
                relativePath to Files.readString(path)
            }.getOrNull()
        }
    }

    private fun parseRuleSources(loaded: List<Pair<String, String>>): List<RuleSource> {
        return loaded.map { (relativePath, content) ->
            RuleSource(
                relativePath = relativePath,
                // A file edited into a temporarily unparseable state keeps its band in the diagram,
                // it just contributes no rules — the same way the editor keeps showing the file.
                rules = runCatching { Parser(input = content).parseRules() }.getOrElse { emptyList() },
            )
        }
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
