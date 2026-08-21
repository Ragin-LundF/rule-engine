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
import androidx.compose.ui.unit.dp
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ui.builder.BuilderRulesController
import ui.builder.RuleAstToBuilderMapper
import ui.builder.model.BuilderRule
import ui.editor.rules.RuleEditorState
import ui.editor.rules.sections.DiagnosticsSection
import ui.editor.rules.sections.StatusBarSection
import ui.editor.rules.sections.TopBarSection
import ui.project.ProjectWorkspace
import ui.project.dialog.ProjectDialogHost
import ui.project.manifest.manifestEntrySelection
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
import ui.workbench.builderCatalogActionsFrom
import ui.workbench.builderCatalogFieldsFrom
import ui.workbench.builderCatalogVariablesFrom
import ui.workbench.catalogActionsFrom
import ui.workbench.catalogFieldsFrom
import ui.workbench.catalogRulesFrom
import ui.workbench.diagram.ExpandedDiagramWindow
import ui.workbench.inspectorSelectionFor
import ui.workbench.model.RuleWorkbenchState
import ui.workbench.model.WorkbenchAction
import ui.workbench.model.mode.AppArea
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
    val builderFieldsAndVariables = remember(key1 = builderCatalogFields, key2 = builderCatalogVariables) {
        builderCatalogFields + builderCatalogVariables
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
        RightPanelController(expanded = state.rightPanelExpanded, viewModel = workbenchViewModel)
    }

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
                    allRuleIds = builderStateMap.keys.filter { it.isNotBlank() },
                    allBuilderRules = allBuilderRules,
                    catalogRules = catalogRules,
                    catalogFields = builderFieldsAndVariables,
                    catalogActions = builderCatalogActions,
                    ruleTreeFiles = ruleTreeFiles,
                    onConditionSelected = { conditionId ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectCondition(conditionId = conditionId),
                        )
                    },
                    testInputState = testInputState,
                    onTestInputStateChange = { testInputState = it },
                    testController = testController,
                    hasErrors = hasErrors,
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SCHEMA -> SchemaAreaContent(
                    state = state,
                    workspace = workspace,
                    expandedDiagramRules = expandedDiagramRules,
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
                    workspace = workspace,
                    expandedDiagramRules = expandedDiagramRules,
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
                    workspace = workspace,
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
        rightPanelWidth = if (state.rightPanelExpanded.value) 320.dp else 56.dp,
    )

    // ── Expanded diagram window ───────────────────────────────────────────────
    // Opened via the "⤢ Expand" button in diagram mode.
    // Shares the same diagramRules state so it updates live while editing.
    if (state.showExpandedDiagram.value) {
        ExpandedDiagramWindow(state = state, rules = expandedDiagramRules)
    }
}
