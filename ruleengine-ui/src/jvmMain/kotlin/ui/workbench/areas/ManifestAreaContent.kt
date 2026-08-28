package ui.workbench.areas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import ui.builder.board.ribbon.RibbonModel
import ui.builder.model.BuilderRule
import ui.components.ModeTabs
import ui.components.header.AreaHeader
import ui.components.header.model.BarDensity
import ui.components.header.model.BindingSpec
import ui.dock.DockController
import ui.dock.manifestEntryRange
import ui.dock.model.DockSurface
import ui.editor.rules.RuleEditorState
import ui.manifest.ManifestIssues
import ui.manifest.ManifestVariableFlow
import ui.manifest.ManifestYamlBridge
import ui.manifest.RuleFileFlow
import ui.manifest.model.ManifestEditorState
import ui.project.ProjectWorkspace
import ui.project.manifest.toEditorState
import ui.schema.IssueLevel
import ui.schema.SchemaIssue
import ui.workbench.ManifestAreaScreen
import ui.workbench.model.catalog.RuleTreeFile
import ui.workbench.model.mode.ManifestMode
import ui.workbench.model.mode.displayName
import ui.workbench.model.mode.icon
import ui.yaml.YamlEditor
import ui.yaml.annotateYaml
import ui.yaml.buildYamlCompletions
import ui.yaml.model.YamlEditorType

/**
 * The Manifest area.
 *
 * The session is the manifest: edits here go straight onto it rather than into a text buffer the
 * saver would regenerate over. The manifest text is only the fallback for when no project is open.
 */
@Suppress("FunctionNaming")
@Composable
fun ManifestAreaContent(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    dock: DockController,
    /** Which tab is open, and where a click on the other one goes: the workbench view model. */
    mode: ManifestMode,
    onModeChange: (ManifestMode) -> Unit,
    /** True while the Inspector is on the manifest itself. */
    manifestSelected: Boolean = false,
    onSelectManifest: () -> Unit = {},
    /** The manifest's paths are navigation: clicking one opens that area. */
    onOpenSchema: () -> Unit = {},
    onOpenActions: () -> Unit = {},
    /**
     * The active entry's rule files in manifest order, and the rules they hold.
     *
     * The same two values the Builder's board reads, for the same question — so the manifest cannot
     * disagree with the board about which read resolves. They describe the *active* entry only, which
     * is why the flow is shown for that entry alone.
     */
    ruleTreeFiles: List<RuleTreeFile> = emptyList(),
    allBuilderRules: List<BuilderRule> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val editorState = workspace.session.value?.toEditorState()
        ?: ManifestYamlBridge.fromYaml(yaml = state.manifestText.value)

    // Generated here rather than read from a buffer: this area has none. `state.manifestText` is only
    // the fallback for when no project is open, and the session is what the saver writes.
    val yaml = ManifestYamlBridge.toYaml(state = editorState)
    // Falls back to the first entry, which is what `RuleEditorState.activeScope` already does and what
    // the CLI does for a manifest without `--entry`. A sample has no session at all, so without this the
    // only entry it has is labelled "not the entry being edited".
    val activeEntryId = workspace.session.value?.activeEntryId ?: editorState.entries.firstOrNull()?.id

    // The one thing about a manifest no single file can show, because it is a property of the order.
    val groups = remember(key1 = ruleTreeFiles, key2 = allBuilderRules) {
        RibbonModel.groups(files = ruleTreeFiles, rules = allBuilderRules)
    }
    val flow = remember(key1 = groups) {
        ManifestVariableFlow.of(groups = groups).associateBy { entry -> entry.relativePath }
    }

    YamlAreaWithDock(
        surface = DockSurface.MANIFEST,
        dock = dock,
        fileName = "manifest.yaml",
        yaml = yaml,
        editorType = YamlEditorType.PROJECT_MANIFEST,
        highlight = activeEntryId?.let { id -> manifestEntryRange(yaml = yaml, entryId = id) },
        issues = ManifestIssues.of(
            state = editorState,
            activeEntryId = activeEntryId,
            fieldTypes = schemaFieldTypes(state = state),
            isLoaded = { path -> path.isBlank() || path in loadedPaths(state = state, editorState = editorState) },
        ) + unresolvedReadIssues(groups = groups, activeEntryId = activeEntryId),
        onSelectIssue = { onSelectManifest() },
        modifier = modifier,
    ) {
        ManifestAreaBody(
            state = state,
            workspace = workspace,
            mode = mode,
            onModeChange = onModeChange,
            editorState = editorState,
            activeEntryId = activeEntryId,
            manifestSelected = manifestSelected,
            onSelectManifest = onSelectManifest,
            onOpenSchema = onOpenSchema,
            onOpenActions = onOpenActions,
            variableFlow = flow,
        )
    }
}

/** The area's own content, with no dock concerns — the same split the Schema and Actions areas use. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ManifestAreaBody(
    state: RuleEditorState,
    workspace: ProjectWorkspace,
    mode: ManifestMode,
    onModeChange: (ManifestMode) -> Unit,
    editorState: ManifestEditorState,
    activeEntryId: String?,
    manifestSelected: Boolean,
    onSelectManifest: () -> Unit,
    onOpenSchema: () -> Unit,
    onOpenActions: () -> Unit,
    variableFlow: Map<String, RuleFileFlow>,
) {
    val loaded = loadedPaths(state = state, editorState = editorState)

    Column(modifier = Modifier.fillMaxSize()) {
    AreaHeader(
        title = "Manifest",
        meta = "${editorState.entries.size} entries",
        // No menu: the manifest is the project's own file, so there is nothing to link, change or
        // unlink. The chip is here anyway because the slot is the same in every area, and an area
        // that answers "which file am I editing" with nothing at all is the gap this closes.
        binding = BindingSpec(label = "File", value = "manifest.yaml"),
        tabs = { density ->
            ModeTabs(
                modes = ManifestMode.entries,
                current = mode,
                label = { tabMode -> tabMode.displayName },
                onSelect = onModeChange,
                icon = { tabMode -> tabMode.icon },
                showLabels = density != BarDensity.MINIMAL,
            )
        },
    )
    ManifestAreaScreen(
        state = editorState,
        mode = mode,
        onStateChange = { edited -> workspace.applyManifestEditorState(edited = edited) },
        activeEntryId = activeEntryId,
        onSelectEntry = { entryId -> workspace.selectEntry(entryId = entryId) },
        onAddEntry = { workspace.addEntry(entryId = workspace.suggestEntryId()) },
        onRemoveEntry = { entryId -> workspace.requestRemoveEntry(entryId = entryId) },
        fromYaml = { yaml ->
            ManifestYamlBridge.fromYaml(yaml = yaml)
        },
        toYaml = { editorState ->
            ManifestYamlBridge.toYaml(state = editorState)
        },
        // The document schema, not `ruleSchema`: a scope names a field of the file as written, and
        // the member schema it produces is exactly what we would be validating it against.
        fieldTypes = schemaFieldTypes(state = state),
        manifestSelected = manifestSelected,
        onSelectManifest = onSelectManifest,
        onOpenSchema = onOpenSchema,
        onOpenActions = onOpenActions,
        isLoaded = { path -> path.isBlank() || path in loaded },
        variableFlow = variableFlow,
        modifier = Modifier.fillMaxSize(),
        yamlEditor = { value, onValueChange, editorModifier ->
            YamlEditor(
                value = value,
                onValueChange = onValueChange,
                modifier = editorModifier,
                editorType = YamlEditorType.PROJECT_MANIFEST,
                annotate = { text ->
                    annotateYaml(text = text, editorType = YamlEditorType.PROJECT_MANIFEST)
                },
                buildCompletions = { context ->
                    buildYamlCompletions(context = context, editorType = YamlEditorType.PROJECT_MANIFEST)
                },
            )
        },
    )
    }
}

/**
 * The manifest-relative paths the editor currently holds a file for.
 *
 * "Loaded", not "exists": the editor knows its working copy and the sample it was handed, and a project
 * that has never been saved has no filesystem to check against — so claiming a file is missing from disk
 * would be a verdict it cannot make.
 */
private fun loadedPaths(state: RuleEditorState, editorState: ManifestEditorState): Set<String> = buildSet {
    addAll(elements = state.inMemoryRuleFiles.value.keys)
    editorState.entries.forEach { entry ->
        if (state.schemaText.value.isNotBlank()) add(element = entry.schemaPath)
        if (state.actionSchemaText.value.isNotBlank()) add(element = entry.actionsPath)
    }
}

/** The document schema's top-level field types, which is what a scope is checked against. */
private fun schemaFieldTypes(state: RuleEditorState): Map<String, String>? =
    state.parsedSchema.value?.fields
        ?.map { (id, definition) -> id.value to definition.type.name.lowercase() }
        ?.toMap()

/**
 * A read no earlier file publishes, as a check of the active entry.
 *
 * A warning rather than an error: the manifest loads and the rules run. What does not happen is the
 * rule firing — which is exactly the failure nothing else in the tool can report, since nothing else
 * looks at more than one file at a time.
 */
private fun unresolvedReadIssues(
    groups: List<ui.builder.board.ribbon.model.RibbonGroup>,
    activeEntryId: String?,
): List<SchemaIssue> = ManifestVariableFlow.unresolvedReads(groups = groups).map { (path, message) ->
    SchemaIssue(
        level = IssueLevel.WARNING,
        path = activeEntryId.orEmpty(),
        message = "$path $message",
    )
}
