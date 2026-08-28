package ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ui.actions.ActionSchemaYamlBridge
import ui.actions.hasValidationIssues
import ui.builder.BuilderRulesController
import ui.builder.RuleAstToBuilderMapper
import ui.builder.model.BuilderRule
import ui.dock.DockController
import ui.editor.rules.RuleEditorState
import ui.editor.rules.model.StatusKind
import ui.editor.rules.sections.DiagnosticsSection
import ui.editor.rules.sections.StatusBarSection
import ui.editor.rules.sections.TopBarSection
import ui.editor.yaml.SyncModelAndYaml
import ui.project.ProjectWorkspace
import ui.project.dialog.ProjectDialogHost
import ui.project.manifest.manifestEntrySelection
import ui.project.manifest.toEditorState
import ui.schema.FieldSchemaYamlBridge
import ui.schema.hasValidationIssues
import ui.settings.SettingsController
import ui.settings.SettingsPersistence
import ui.settings.SettingsScreen
import ui.tester.RuleTestController
import ui.workbench.AppAreaIconRail
import ui.workbench.RightPanelController
import ui.workbench.RightPanelPersistence
import ui.workbench.RuleWorkbenchScreen
import ui.workbench.RuleWorkbenchViewModel
import ui.workbench.WorkbenchRightPanel
import ui.workbench.areas.ActionsAreaContent
import ui.workbench.areas.ManifestAreaContent
import ui.workbench.areas.RulesAreaContent
import ui.workbench.areas.SamplesAreaContent
import ui.workbench.areas.SchemaAreaContent
import ui.workbench.areas.applyActionsYaml
import ui.workbench.areas.applySchemaYaml
import ui.workbench.builderCatalogActionsFrom
import ui.workbench.builderCatalogFieldsFrom
import ui.workbench.builderCatalogVariablesFrom
import ui.workbench.catalogActionsFrom
import ui.workbench.catalogFieldsFrom
import ui.workbench.catalogRulesFrom
import ui.workbench.diagram.ExpandedDiagramWindow
import ui.workbench.inspectorSelectionFor
import ui.workbench.model.CanvasSelection
import ui.workbench.model.InspectorItem
import ui.workbench.model.RuleWorkbenchState
import ui.workbench.model.WorkbenchAction
import ui.workbench.model.mode.ActionMode
import ui.workbench.model.mode.AppArea
import ui.workbench.model.mode.SchemaMode
import ui.workbench.rules.ruleTreeFilesFrom
import ui.workbench.rules.toViewMode
import ui.workbench.uiDiagnosticsFrom

// ── Main composable ───────────────────────────────────────────────────────────

// Down from 925 lines to ~209: the effects, the derivations, every area's content and both panels
// now live in their own files. What is left is the screen's wiring — the state it owns, the
// derivations that feed the slots, and one call filling those slots — and it does not compress
// further without hiding the layout behind indirection that makes it harder to read, not easier.
// `CyclomaticComplexMethod` was suppressed here too and is no longer needed.
@Suppress("LongMethod")
@Composable
actual fun RuleEditor(closeController: AppCloseController, saveController: AppSaveController) {
    val scope = rememberCoroutineScope()

    // Root workbench state (navigation, selection, panel tabs).
    val workbenchViewModel = remember {
        RuleWorkbenchViewModel(
            initialState = RuleWorkbenchState.Empty.copy(rightPanelTab = RightPanelPersistence.loadTab()),
        )
    }
    val workbenchState by workbenchViewModel.state.collectAsState()

    // Centralized state container for the editor content (text, parsed schema, diagnostics).
    // The panel's stored open state is applied here rather than in a LaunchedEffect: an effect runs
    // after the first composition, so a restored-open panel would render collapsed for one frame.
    val state = remember {
        RuleEditorState(scope = scope).also { editorState ->
            editorState.rightPanelExpanded.value = RightPanelPersistence.loadExpanded()
        }
    }

    // Owns the project on disk: open, new, save, and linking shared schema/action files.
    val workspace = remember { ProjectWorkspace(state = state) }

    DisposableEffect(key1 = closeController, key2 = workspace) {
        closeController.onCloseRequested = workspace::requestClose
        onDispose { closeController.onCloseRequested = null }
    }

    // Guarded on `isDirty` so the shortcut does exactly what the toolbar button does — the button is
    // disabled when clean, and an unguarded save on a pristine project would open the native Save
    // dialog through `createScratchSession()`.
    DisposableEffect(key1 = saveController, key2 = workspace) {
        saveController.onSaveRequested = { if (workspace.isDirty) workspace.saveProject() }
        onDispose { saveController.onSaveRequested = null }
    }

    val closeApproved by workspace.closeRequested
    LaunchedEffect(key1 = closeApproved) {
        if (closeApproved) closeController.confirmClose()
    }

    // Keep the legacy editor view mode in sync with the workbench rule mode.
    LaunchedEffect(key1 = workbenchState.ruleMode) {
        state.viewMode.value = workbenchState.ruleMode.toViewMode()
    }

    // Opening a project is an explicit ProjectLoader call, not a side effect of the parsed manifest
    // changing: keying an effect on a data class meant re-opening an equal-but-different manifest
    // never fired, and the "only when nothing is selected" guard meant the second project of a
    // session silently kept the first one's rules.

    RuleEditorTextEffects(state = state)

    // ── Test panel state ──────────────────────────────────────────────────────
    // Created by remember with no keys, so the controller and its caret-visible run state live for
    // the whole window session exactly as the `remember { mutableStateOf(...) }` it replaces did.
    val testController = remember { RuleTestController(state = state, scope = scope) }
    var testInputState by testController.input

    // ── Parsed rules of the open file, for the builder and the rule roster ─────
    val diagramRulesForWindow = remember(key1 = state.ruleValue.value.text) {
        runCatching { Parser(input = state.ruleValue.value.text).parseRules() }.getOrElse { emptyList() }
    }

    // ── Parsed rules for the expanded diagram window ───────────────────────────
    // The one buffer holds whatever the entry is showing — a single file or all of them — so
    // expanding a view cannot narrow or widen what it draws.
    val expandedDiagramRules = remember(key1 = state.ruleValue.value.text) {
        runCatching { Parser(input = state.ruleValue.value.text).parseRules() }.getOrElse { emptyList() }
    }

    // ── All builder rules derived from all parsed rule ASTs ──────────────────────
    val allBuilderRules = remember(key1 = diagramRulesForWindow) {
        if (diagramRulesForWindow.isEmpty()) {
            listOf(BuilderRule.None)
        } else {
            diagramRulesForWindow.map { ast -> RuleAstToBuilderMapper.map(ast) }
        }
    }

    // ── Builder rules: selection and one editor state per rule ────────────────
    // remember with no keys: the controller and its state map outlive every re-parse, which is what
    // keeps the rule list alive across tab switches.
    val builderRules = remember {
        BuilderRulesController(
            ruleText = { state.ruleValue.value.text },
            // TextFieldValue without a selection: the caret returns to offset 0 after a rename or an
            // add, exactly as it did before.
            onRuleTextChange = { text -> state.ruleValue.value = TextFieldValue(text = text) },
        )
    }
    val builderStateMap by builderRules.stateMap

    // Two effects, in this order, on purpose: Compose applies them in declaration order, and that
    // decides whether the active state below resolves against the old or the new map on the first
    // frame after a parse. Do not merge them.
    LaunchedEffect(key1 = allBuilderRules, key2 = state.selectedManifestEntry.value) {
        builderRules.syncSelection(
            rules = allBuilderRules,
            preferredId = state.selectedManifestEntry.value,
        )
    }

    LaunchedEffect(key1 = allBuilderRules) {
        builderRules.rebuildStateMap(rules = allBuilderRules)
    }

    val activeBuilderEditorState = builderRules.activeState()

    // ── Catalog data derived from parsed schema/actions/rules ─────────────────
    // The remember keys stay here on purpose: they are what decides when each list goes stale, and
    // catalogRules deliberately does not key on the diagnostics it reads.
    // Keyed on the parsed rules as well as on the schema: the inspector's usage count is a fact about
    // the rules, so it has to go stale when they change.
    val catalogFields = remember(
        key1 = state.parsedSchema.value,
        key2 = state.activeScope,
        key3 = diagramRulesForWindow,
    ) {
        catalogFieldsFrom(schema = state.ruleSchema, rules = diagramRulesForWindow)
    }
    val builderCatalogFields = remember(key1 = state.parsedSchema.value, key2 = state.activeScope) {
        builderCatalogFieldsFrom(schema = state.ruleSchema)
    }
    // Variables in scope depend on which rule is open, so this keys on the selected rule as well as
    // on the entry's text. Appended to the schema fields so every operand picker offers them without
    // a second catalog to thread through the tree.
    val builderCatalogVariables = remember(
        key1 = state.selectedManifestEntry.value,
        key2 = state.ruleValue.value.text,
        key3 = activeBuilderEditorState.ruleId,
    ) {
        builderCatalogVariablesFrom(
            // The open buffer, not the saved file: a variable has to reach the operand picker as
            // soon as its row is added, not once the file is written.
            files = state.parsedRuleFilesForCurrentEntryWithOpenBuffer(),
            uptoRuleId = activeBuilderEditorState.ruleId.takeIf { it.isNotBlank() },
        )
    }
    // `withVariables` rather than list concatenation: a plain `+` yields a List and would drop the
    // bare-alias index the catalog carries.
    val builderFieldsAndVariables = remember(key1 = builderCatalogFields, key2 = builderCatalogVariables) {
        builderCatalogFields.withVariables(variables = builderCatalogVariables)
    }
    val catalogActions = remember(key1 = state.parsedActionSchema.value, key2 = diagramRulesForWindow) {
        catalogActionsFrom(actions = state.parsedActionSchema.value, rules = diagramRulesForWindow)
    }
    val builderCatalogActions = remember(key1 = state.parsedActionSchema.value) {
        builderCatalogActionsFrom(actions = state.parsedActionSchema.value)
    }
    val hasErrors = state.diagnosticsList.value.any { it.severity == Severity.ERROR }
    val uiDiagnostics = remember(key1 = state.diagnosticsList.value) {
        uiDiagnosticsFrom(diagnostics = state.diagnosticsList.value)
    }
    val catalogRules = remember(key1 = diagramRulesForWindow, key2 = hasErrors) {
        catalogRulesFrom(
            rules = diagramRulesForWindow,
            hasErrors = hasErrors,
            diagnosticsEmpty = state.diagnosticsList.value.isEmpty(),
            ruleTextNotBlank = state.ruleValue.value.text.isNotBlank(),
        )
    }

    // ── Rule tree for Builder mode: one file node per manifest rule file ─────
    val ruleTreeFiles = remember(
        key1 = state.selectedManifestEntry.value,
        key2 = state.ruleValue.value.text,
        key3 = state.diagnosticsList.value,
    ) {
        ruleTreeFilesFrom(
            parsedFiles = state.parsedRuleFilesForCurrentEntry(),
            fallbackRuleIds = builderStateMap.keys,
            currentFile = state.selectedManifestRuleFile.value,
            diagnostics = state.diagnosticsList.value,
        )
    }

    // ── What the Inspector describes ──────────────────────────────────────────
    // Derived here rather than dispatched from each click site. Every way of choosing a rule — the
    // rule tree, the table, the builder's own header, a rename, an added rule, a sample, a switch of
    // manifest file — lands in `BuilderRulesController.selectedId` and shows up as
    // `activeBuilderEditorState.ruleId`, so reading that one value cannot miss a path, and a new path
    // cannot forget to dispatch.
    // Keyed on the whole `TextFieldValue`, so a caret move invalidates it as surely as an edit does —
    // in code mode the caret *is* the selection.
    val inspectorSelection = remember(
        workbenchState.ruleMode,
        state.ruleValue.value,
        catalogRules,
        activeBuilderEditorState.ruleId,
        uiDiagnostics,
    ) {
        inspectorSelectionFor(
            ruleMode = workbenchState.ruleMode,
            ruleText = state.ruleValue.value.text,
            ruleIds = catalogRules.map { it.id },
            caret = state.ruleValue.value.selection.start,
            builderRuleId = activeBuilderEditorState.ruleId,
            diagnostics = uiDiagnostics,
        )
    }

    // Guarded on the area, not fired unconditionally: a field or action selected in the Schema or
    // Actions area has to survive until the user comes back to the rules, and re-entering the Rules
    // area is what puts the rule back. Within the Rules area a rule change deliberately does replace
    // a selected condition — that condition belonged to the rule just left.
    LaunchedEffect(key1 = inspectorSelection.ruleId, key2 = workbenchState.appArea) {
        if (workbenchState.appArea == AppArea.RULES) {
            workbenchViewModel.dispatch(
                action = WorkbenchAction.SelectRule(ruleId = inspectorSelection.ruleId),
            )
        }
    }

    val rightPanel = remember {
        RightPanelController(
            expanded = state.rightPanelExpanded,
            width = state.rightPanelWidth,
            viewModel = workbenchViewModel,
        )
    }

    // One dock controller for every area: the height is shared, the open state is per surface, and both
    // are persisted. Held here rather than per area so switching area cannot reset either.
    val dock = remember {
        DockController(
            height = state.dockHeight,
            expanded = state.dockExpanded,
            tab = state.dockTab,
        )
    }

    // Kept composed for every area, not just the one that draws the editor: the Inspector can edit a
    // field or an action while another area is on screen, and an effect that is not composed cannot push
    // that edit to the YAML.
    SyncModelAndYaml(
        sync = state.schemaEditor,
        yaml = state.schemaText.value,
        textMode = workbenchState.schemaMode == SchemaMode.YAML,
        fromYaml = { yaml -> FieldSchemaYamlBridge.fromYaml(yaml = yaml) },
        toYaml = { editorState -> FieldSchemaYamlBridge.toYaml(state = editorState) },
        hasIssues = { editorState -> editorState.hasValidationIssues() },
        isReadOnly = { editorState -> editorState.isReadOnly },
        onYamlChange = { newYaml -> state.applySchemaYaml(yaml = newYaml) },
        parseError = "Invalid YAML: could not parse field schema",
    )
    SyncModelAndYaml(
        sync = state.actionEditor,
        yaml = state.actionSchemaText.value,
        textMode = workbenchState.actionMode == ActionMode.YAML,
        fromYaml = { yaml -> ActionSchemaYamlBridge.fromYaml(yaml = yaml) },
        toYaml = { editorState -> ActionSchemaYamlBridge.toYaml(state = editorState) },
        hasIssues = { editorState -> editorState.hasValidationIssues() },
        isReadOnly = { editorState -> editorState.isReadOnly },
        onYamlChange = { newYaml -> state.applyActionsYaml(yaml = newYaml) },
        parseError = "Invalid YAML: could not parse action schema",
    )

    ProjectDialogHost(workspace = workspace)

    RuleWorkbenchScreen(
        topBar = {
            TopBarSection(
                workspace = workspace,
                entrySelection = manifestEntrySelection(
                    session = workspace.session.value,
                    parsedManifest = state.parsedManifest.value,
                    selectedEntryId = state.selectedManifestEntry.value,
                ),
                onManageEntries = {
                    workbenchViewModel.dispatch(
                        action = WorkbenchAction.SelectAppArea(area = AppArea.MANIFEST),
                    )
                },
                inspectorOpen = rightPanel.isInspectorOpen,
                onToggleInspector = rightPanel::toggleInspector,
            )
        },
        iconRail = {
            AppAreaIconRail(
                selectedArea = workbenchState.appArea,
                onAreaSelected = { area ->
                    workbenchViewModel.dispatch(action = WorkbenchAction.SelectAppArea(area = area))
                },
                modifier = Modifier.fillMaxHeight(),
            )
        },
        centerContent = {
            val canvasSelection = CanvasSelection.of(item = workbenchState.selectedInspectorItem)

            when (workbenchState.appArea) {
                AppArea.RULES -> RulesAreaContent(
                    state = state,
                    scope = scope,
                    ruleMode = workbenchState.ruleMode,
                    onRuleModeChange = { mode ->
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectRuleMode(mode = mode))
                    },
                    builderRules = builderRules,
                    builderEditorState = activeBuilderEditorState,
                    allBuilderRules = allBuilderRules,
                    catalogRules = catalogRules,
                    catalogActions = builderCatalogActions,
                    ruleTreeFiles = ruleTreeFiles,
                    // A gesture the Builder refuses — emptying a `when`, or promoting a row whose
                    // operator has no value-expression form — says why here rather than doing nothing.
                    onBuilderMessage = { message ->
                        state.setStatus(msg = message, kind = StatusKind.ERROR)
                    },
                    // The canvas highlights whatever the Inspector is pointed at, and moves it. Both
                    // read the one selection the workbench already holds, so they cannot disagree.
                    selectedNodeId = canvasSelection.nodeId,
                    selectedStatementId = canvasSelection.statementId,
                    selectedSteps = canvasSelection.steps,
                    onSelectNode = { nodeId, steps ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectInspectorItem(
                                item = InspectorItem.Condition(conditionId = nodeId, steps = steps),
                            ),
                        )
                        rightPanel.showInspector()
                    },
                    onSelectStatement = { branch, statementId ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectInspectorItem(
                                item = InspectorItem.Statement(
                                    branch = branch,
                                    statementId = statementId,
                                ),
                            ),
                        )
                        rightPanel.showInspector()
                    },
                    testInputState = testInputState,
                    onTestInputStateChange = { testInputState = it },
                    testController = testController,
                    hasErrors = hasErrors,
                    dock = dock,
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SCHEMA -> SchemaAreaContent(
                    state = state,
                    mode = workbenchState.schemaMode,
                    onModeChange = { mode ->
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectSchemaMode(mode = mode))
                    },
                    workspace = workspace,
                    expandedDiagramRules = expandedDiagramRules,
                    dock = dock,
                    selectedFieldId = canvasSelection.fieldId,
                    // One count per dotted path, from the same catalog the Inspector reads, so the row's
                    // tag and the delete guard cannot disagree about who reads a field.
                    readBy = catalogFields.associate { field -> field.id to field.usages },
                    onMessage = { message -> state.setStatus(msg = message, kind = StatusKind.ERROR) },
                    modifier = Modifier.fillMaxSize(),
                    onInspectField = { fieldId ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectField(fieldId = fieldId),
                        )
                        rightPanel.showInspector()
                    },
                )

                AppArea.ACTIONS -> ActionsAreaContent(
                    state = state,
                    mode = workbenchState.actionMode,
                    onModeChange = { mode ->
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectActionMode(mode = mode))
                    },
                    workspace = workspace,
                    expandedDiagramRules = expandedDiagramRules,
                    dock = dock,
                    selectedActionName = canvasSelection.actionName,
                    emittedBy = catalogActions.associate { action -> action.name to action.usages },
                    onMessage = { message -> state.setStatus(msg = message, kind = StatusKind.ERROR) },
                    modifier = Modifier.fillMaxSize(),
                    onInspectAction = { actionName ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectAction(actionName = actionName),
                        )
                        rightPanel.showInspector()
                    },
                )

                AppArea.MANIFEST -> ManifestAreaContent(
                    state = state,
                    mode = workbenchState.manifestMode,
                    onModeChange = { mode ->
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectManifestMode(mode = mode))
                    },
                    workspace = workspace,
                    dock = dock,
                    // What the board reads, for the question only the manifest can answer: whether the
                    // order of these files leaves a `$variable` read with nothing before it to publish.
                    ruleTreeFiles = ruleTreeFiles,
                    allBuilderRules = allBuilderRules,
                    manifestSelected = workbenchState.selectedInspectorItem is InspectorItem.Manifest,
                    onSelectManifest = {
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectInspectorItem(
                                item = InspectorItem.Manifest(name = state.manifestText.value),
                            ),
                        )
                        rightPanel.showInspector()
                    },
                    // The manifest's whole job is to point at the other three files, so its paths point
                    // back: clicking one is how you get to what it names.
                    onOpenSchema = {
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectAppArea(area = AppArea.SCHEMA))
                    },
                    onOpenActions = {
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectAppArea(area = AppArea.ACTIONS))
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SAMPLES -> SamplesAreaContent(
                    state = state,
                    workspace = workspace,
                    scope = scope,
                    onSampleApplied = {
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectAppArea(area = AppArea.RULES),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Left inline: a pass-through to a screen that reads no editor state, so a file of
                // its own would hold nothing but this call.
                AppArea.SETTINGS -> SettingsScreen(
                    shortcut = SettingsController.autoCompleteShortcut,
                    onShortcutChange = { shortcut ->
                        SettingsController.setAutoCompleteShortcut(
                            shortcut = shortcut,
                            persist = SettingsPersistence::saveAutoCompleteShortcut,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        rightPanel = {
            WorkbenchRightPanel(
                state = state,
                tab = workbenchState.rightPanelTab,
                onTabChange = rightPanel::selectTab,
                selectedInspectorItem = workbenchState.selectedInspectorItem,
                catalogFields = catalogFields,
                catalogActions = catalogActions,
                catalogRules = catalogRules,
                builderEditorState = activeBuilderEditorState,
                ruleStates = builderStateMap,
                builderFields = builderFieldsAndVariables,
                // The Inspector edits, so it writes rule text the same way the canvas does.
                onBuilderDslChange = { newDsl ->
                    builderRules.applyDsl(ruleId = activeBuilderEditorState.ruleId, newDsl = newDsl)
                },
                onBuilderMessage = { message ->
                    state.setStatus(msg = message, kind = StatusKind.ERROR)
                },
                onSelectInspectorItem = { item ->
                    workbenchViewModel.dispatch(action = WorkbenchAction.SelectInspectorItem(item = item))
                },
                manifestState = workspace.session.value?.toEditorState(),
                onManifestChange = { edited -> workspace.applyManifestEditorState(edited = edited) },
                activeEntryId = workspace.session.value?.activeEntryId,
                // The document schema, not `ruleSchema`: a scope names a field of the file as written.
                schemaFieldTypes = state.parsedSchema.value?.fields
                    ?.map { (id, definition) -> id.value to definition.type.name.lowercase() }
                    ?.toMap(),
                // Relativized by the workspace, because only it knows where the manifest lives.
                chooseManifestPath = { kind -> workspace.choosePathForManifest(kind = kind) },
                chooseManifestPathDisabledReason = workspace.chosenPathBlockedReason,
                uiDiagnostics = inspectorSelection.diagnostics,
                testInputState = testInputState,
                onTestInputStateChange = { testInputState = it },
                testController = testController,
                onToggleExpanded = rightPanel::toggleExpanded,
            )
        },
        bottomBar = {
            DiagnosticsSection(state = state)
            StatusBarSection(state = state, workspace = workspace)
        },
        rightPanelWidth = rightPanelWidthOf(state = state),
        onRightPanelResize = rightPanelResizeOf(state = state, controller = rightPanel),
    )

    // ── Expanded diagram window ───────────────────────────────────────────────
    // Opened via the "Expand" action in diagram mode.
    // Shares the same diagramRules state so it updates live while editing.
    if (state.showExpandedDiagram.value) {
        ExpandedDiagramWindow(state = state, rules = expandedDiagramRules)
    }
}

/** How wide the right panel is: the dragged width while open, the icon strip's width while closed. */
private fun rightPanelWidthOf(state: RuleEditorState): Dp {
    return if (state.rightPanelExpanded.value) {
        state.rightPanelWidth.value.dp
    } else {
        COLLAPSED_PANEL_WIDTH
    }
}

/**
 * The shell's resize callback, or null when the panel should not be resizable.
 *
 * Null while collapsed: a collapsed panel is an icon strip with one correct width, and a handle that
 * silently refuses to move is worse than no handle at all.
 *
 * Every delta goes through [RightPanelController.setWidth] rather than to the state, so the clamp that
 * keeps the width usable is applied in one place — see `RightPanelWidthTest`.
 */
private fun rightPanelResizeOf(
    state: RuleEditorState,
    controller: RightPanelController,
): ((Dp) -> Unit)? {
    if (!state.rightPanelExpanded.value) {
        return null
    }
    return { delta -> controller.setWidth(value = state.rightPanelWidth.value + delta.value) }
}

/** The icon strip's width when the right panel is closed — wide enough for its vertical tab labels. */
private val COLLAPSED_PANEL_WIDTH = 56.dp
