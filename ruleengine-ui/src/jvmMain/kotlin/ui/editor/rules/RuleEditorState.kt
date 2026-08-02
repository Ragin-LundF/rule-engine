package ui.editor.rules

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import ruleengine.core.domain.dto.action.ActionSchema
import ruleengine.core.domain.dto.field.FieldSchema
import ruleengine.core.errors.ValidationDiagnostic
import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestPathResolution
import ruleengine.manifest.ManifestPathResolver
import ruleengine.manifest.ProjectManifest
import ui.autocompletion.model.CompletionItem
import ui.diagrams.model.DiagramViewKind
import ui.diagrams.model.RuleSource
import ui.dsl.model.DslCursorContext
import ui.dsl.model.DslSection
import ui.editor.CodeEditing
import ui.editor.rules.model.StatusKind
import ui.editor.rules.model.ViewMode
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

    /**
     * The entry's rule files held in memory, keyed by the manifest-relative path — the sample case.
     *
     * A bundled sample arrives as Compose resources and has no [manifestBaseDir] to read from. Without
     * this, everything that loads rule files by path silently produced nothing for a sample: switching to
     * a single file reported "Manifest base directory is not set", and the All-files view and every
     * diagram came up empty.
     *
     * It is a fallback rather than a second code path: the readers below prefer it when it is populated,
     * so a sample goes through exactly the same file switching, All-files view and diagrams as a project
     * on disk. Empty for a project, which is what keeps disk the default.
     */
    val inMemoryRuleFiles: MutableState<Map<String, String>> = mutableStateOf(value = emptyMap())

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

    /** Where an open completion popup was anchored; -1 when none is open. See `CodeEditing.isAnchorLive`. */
    val autoCompleteAnchor: MutableState<Int> = mutableStateOf(value = -1)

    /** Set when a space-based shortcut fired, so the editor can drop the space it may also insert. */
    val swallowShortcutSpace: MutableState<Boolean> = mutableStateOf(value = false)
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

    /**
     * Clears every buffer that belongs to a loaded project.
     *
     * Opening a project has to start from nothing, not from whatever the previous one left behind:
     * an entry without a `schema:` key must show an empty schema rather than inheriting the last
     * project's, and a stale [selectedManifestEntry] is what used to make a second load do nothing
     * at all. Panel layout (split fraction, expanded panels, theme-ish preferences) is deliberately
     * left alone — that is the user's workspace, not the project's content.
     */
    fun reset() {
        resetEntryBuffers()

        manifestText.value = ""
        manifestFieldValue.value = TextFieldValue(text = "")
        manifestBaseDir.value = null
        parsedManifest.value = null
        selectedManifestEntry.value = null
        // Belongs to the manifest, not to an entry: a project opened after a sample must read from disk
        // again rather than keep finding the sample's files by the same relative paths.
        inMemoryRuleFiles.value = emptyMap()
    }

    /**
     * Clears everything that belongs to one manifest entry, leaving the manifest itself in place.
     *
     * Switching entries has to start from nothing the same way opening a project does — an entry
     * without a `schema:` key must show an empty schema rather than the previous entry's — but the
     * manifest, its base directory and the parsed model describe the whole project and outlive the
     * switch.
     */
    fun resetEntryBuffers() {
        schemaText.value = ""
        schemaFieldValue.value = TextFieldValue(text = "")
        parsedSchema.value = null

        actionSchemaText.value = ""
        actionFieldValue.value = TextFieldValue(text = "")
        parsedActionSchema.value = null

        ruleValue.value = TextFieldValue(text = "")
        allRulesText.value = ""
        showAllRules.value = false
        entryRuleSources.value = emptyList()
        selectedManifestRuleFile.value = null

        diagnosticsList.value = emptyList()
        diagnosticsText.value = ""

        showAutoComplete.value = false
        autoCompleteWord.value = ""
        autoCompleteIndex.value = 0
        autoCompleteWordStart.value = 0
    }

    /** Accept an autocomplete suggestion and insert it at the current cursor position. */
    fun acceptSuggestion(item: CompletionItem) {
        val edit = CodeEditing.applySuggestion(
            text = ruleValue.value.text,
            wordStart = autoCompleteWordStart.value,
            cursor = ruleValue.value.selection.start,
            insertText = item.insertText,
        )
        ruleValue.value = TextFieldValue(text = edit.text, selection = TextRange(index = edit.cursor))
        showAutoComplete.value = false
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
        loadRuleFiles(relativePaths = currentEntryRulePaths())
    }

    /**
     * The same, for a caller that already knows which files it wants.
     *
     * The project loader is one: it activates an entry whose paths come from the session, and going
     * back through [parsedManifest] would tie loading to a buffer that has not necessarily caught up
     * with the entry being switched to.
     */
    fun loadRuleFiles(relativePaths: List<String>) {
        val loaded = readRuleFiles(relativePaths = relativePaths)
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
        return parseRuleSources(loaded = readRuleFiles(relativePaths = currentEntryRulePaths()))
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

    private fun currentEntryRulePaths(): List<String> {
        return parsedManifest.value?.entries?.find { it.id == selectedManifestEntry.value }?.rules.orEmpty()
    }

    private fun readRuleFiles(relativePaths: List<String>): List<Pair<String, String>> {
        val inMemory = inMemoryRuleFiles.value
        if (inMemory.isNotEmpty()) {
            return relativePaths.mapNotNull { relativePath ->
                inMemory[relativePath]?.let { content -> relativePath to content }
            }
        }

        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() }
            ?: return emptyList()

        return relativePaths.mapNotNull { relativePath ->
            runCatching {
                val path = resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule")
                relativePath to Files.readString(path)
            }.getOrNull()
        }
    }

    /**
     * Parses `(relativePath, content)` pairs into the per-file model the manifest run diagram reads.
     *
     * Not private, because a sample arrives as resource text rather than as files on disk: it has the
     * same file grouping and needs the same diagram, so it goes through this rather than through a
     * second parse that could disagree with it.
     */
    fun parseRuleSources(loaded: List<Pair<String, String>>): List<RuleSource> {
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

        // The sample case: the file is already in memory, so there is nothing to read and no base
        // directory to need. Checked first, because a sample has no base directory at all and would
        // otherwise fail below with a message about a directory the user never chose.
        inMemoryRuleFiles.value[relativePath]?.let { content ->
            ruleValue.value = TextFieldValue(text = content)
            selectedManifestRuleFile.value = relativePath
            diagnosticsText.value = ""
            diagnosticsList.value = emptyList()
            setStatus(msg = "Loaded ${relativePath.substringAfterLast(delimiter = '/')}", kind = StatusKind.SUCCESS)
            return
        }

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
