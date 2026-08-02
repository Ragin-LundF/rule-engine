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
    /**
     * True while the buffer holds the whole entry rather than one of its files.
     *
     * The text itself is in [ruleValue] either way — there is one buffer, and this says what it
     * currently contains. Rule saving is skipped while it is set, because several files joined cannot
     * be written back to one.
     */
    val showAllRules: MutableState<Boolean> = mutableStateOf(value = false)

    /**
     * The current entry's rule files parsed one by one, keeping the file each rule came from.
     *
     * The joined buffer cannot answer that: the join is exactly where the provenance is lost. The
     * manifest run diagram needs it to label the file bands that show grouping into files is an
     * organisation choice and not a runtime boundary.
     */
    val entryRuleSources: MutableState<List<RuleSource>> = mutableStateOf(value = emptyList())

    /**
     * The entry's rule files as they currently stand, keyed by the manifest-relative path.
     *
     * This is the working copy, and every reader below prefers it. Disk is read **once**, by the
     * explicit load that fills this map; navigating between files, switching to All files and every
     * diagram then work from here. Re-reading a file on navigation is what used to hand back the text
     * on disk and silently discard whatever the Builder had changed since.
     *
     * A bundled sample populates it directly — it arrives as Compose resources and has no
     * [manifestBaseDir] to read from — which is now the same arrangement a project uses rather than a
     * second code path.
     *
     * Emptied by [reset], so opening or creating a project is the one action that goes back to disk.
     * An entry switch keeps it: the map is keyed by path across the whole project, and a file edited in
     * one entry is still the working copy when that entry comes back.
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

    /**
     * Whether the right inspector/simulate panel is expanded to its full width or collapsed to a strip.
     *
     * Starts collapsed. Expanded, it and the rule tree together leave the center panel too little
     * width for the Builder's rows, whose dropdowns and value boxes then overlap; the strip is one
     * click from opening, and the layout it opens into is one the user chose.
     */
    val rightPanelExpanded: MutableState<Boolean> = mutableStateOf(value = false)

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
     * Load all rule files for the current manifest entry into [ruleValue] and [entryRuleSources].
     *
     * Both come out of the same read so they can never disagree about which files were loaded: the
     * joined text is what every view consumes, while the per-file parse is what the manifest run
     * diagram needs, since joining the files first would lose which file a rule was written in.
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
        // Already showing the whole entry: the buffer holds it, and re-reading the files would drop
        // whatever has been edited since. The joined buffer cannot be attributed back to the files it
        // came from, so there is nothing to stash and nothing to gain by reloading.
        if (showAllRules.value && ruleValue.value.text.isNotBlank() && relativePaths == currentEntryRulePaths()) {
            return
        }
        stashOpenBufferInMemory()
        val loaded = readRuleFiles(relativePaths = relativePaths)
        // Fills the working copy on the way through, so this is the last time disk is read until the
        // user explicitly loads again.
        inMemoryRuleFiles.value = loaded.toMap()
        // Into the one buffer every view reads. The Builder writes there, the code editor shows it and
        // the tester runs it; a second copy of the same text is what let the Builder's edits and the
        // other views disagree about what the entry says.
        ruleValue.value = TextFieldValue(text = loaded.joinToString(separator = "\n\n") { (_, c) -> c })
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

    /**
     * The same, with the open buffer's text in place of the file it was loaded from.
     *
     * [parsedRuleFilesForCurrentEntry] deliberately reads only what is saved, which is right for an
     * export and wrong for anything that has to keep up with typing. The Builder's operand picker is
     * the case in point: a variable must be offered as soon as its `set` or `add` row exists, which is
     * long before the file reaches disk — otherwise the row that declares `$topics` and the row that
     * reads it can never both be on screen.
     *
     * What the buffer *represents* depends on how the entry was opened, and this follows it either
     * way — the rule being: read the same text the Builder parses.
     *
     * A project opens one file at a time ([selectedManifestRuleFile] set), so the buffer replaces that
     * file and the rest of the entry still contributes. A sample opens with every file concatenated
     * and no file selected, so the buffer *is* the entry and stands alone. Reading the per-file list
     * in that second case is what used to hide a variable until the sample was saved to disk — which
     * for a sample never happens.
     */
    fun parsedRuleFilesForCurrentEntryWithOpenBuffer(): List<RuleSource> {
        val openPath = selectedManifestRuleFile.value
            ?: return parseRuleSources(loaded = listOf(WHOLE_ENTRY_BUFFER to ruleValue.value.text))

        val paths = currentEntryRulePaths()
        return parseRuleSources(
            loaded = withOpenBuffer(
                paths = paths,
                saved = readRuleFiles(relativePaths = paths).toMap(),
                openPath = openPath,
                bufferText = ruleValue.value.text,
            )
        )
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

    /**
     * Writes the open buffer back into [inMemoryRuleFiles] before something replaces it.
     *
     * The map is the working copy, so it has to keep up with the buffer: a file switch reads from it,
     * and without this it would hand back the text as of the last explicit load and silently drop
     * everything edited since. Saving is what writes through to disk; this only keeps the session
     * consistent with itself.
     *
     * Only when a single file is open. In "All files" the buffer is the whole entry concatenated, and
     * there is no way to attribute it back to the files it came from.
     */
    private fun stashOpenBufferInMemory() {
        if (showAllRules.value || inMemoryRuleFiles.value.isEmpty()) {
            return
        }
        val relativePath = selectedManifestRuleFile.value ?: return
        if (relativePath !in inMemoryRuleFiles.value) {
            return
        }
        inMemoryRuleFiles.value = inMemoryRuleFiles.value + (relativePath to ruleValue.value.text)
    }

    /**
     * The working copy when there is one, and disk otherwise.
     *
     * The disk branch is reached only before the first [loadRuleFiles] of an entry — after that the
     * map is authoritative, which is what keeps an unsaved edit from being read back over.
     */
    private fun readRuleFiles(relativePaths: List<String>): List<Pair<String, String>> {
        val inMemory = inMemoryRuleFiles.value
        val base = manifestBaseDir.value?.let { Path.of(it).toAbsolutePath().normalize() }

        // Per path, not all-or-nothing: the working copy holds the entry that was open, and switching
        // to another entry asks for paths it has never seen. Falling back for each one keeps an edited
        // file in the buffer while a file from a different entry is still read.
        return relativePaths.mapNotNull { relativePath ->
            val content = inMemory[relativePath]
                ?: base?.let { dir -> readRuleFileFromDisk(base = dir, relativePath = relativePath) }
            content?.let { text -> relativePath to text }
        }
    }

    private fun readRuleFileFromDisk(base: Path, relativePath: String): String? {
        return runCatching {
            Files.readString(resolveManifestPathOrThrow(baseDir = base, relativePath = relativePath, label = "rule"))
        }.getOrNull()
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
        stashOpenBufferInMemory()
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

/**
 * Stands in for a file name when the buffer holds the whole entry rather than one of its files.
 *
 * Only ever used as a [RuleSource] label; nothing resolves it as a path.
 */
internal const val WHOLE_ENTRY_BUFFER: String = "<entry>"

/**
 * The entry's `(relativePath, content)` pairs with [bufferText] standing in for [openPath].
 *
 * Kept as a free function so it can be tested without a manifest, a base directory or a file on
 * disk. Order follows [paths], which is the manifest's — load-bearing, because a `set` publishes only
 * to the rules after it and an `add` only from its own rule onward.
 *
 * A path with no saved content is dropped unless it is the open one, so a rule file created but not
 * yet written still contributes the variables its buffer declares.
 */
internal fun withOpenBuffer(
    paths: List<String>,
    saved: Map<String, String>,
    openPath: String?,
    bufferText: String,
): List<Pair<String, String>> {
    return paths.mapNotNull { path ->
        val content = if (path == openPath) bufferText else saved[path]
        content?.let { text -> path to text }
    }
}
