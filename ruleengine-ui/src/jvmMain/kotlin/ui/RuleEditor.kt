package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.Surface
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ruleengine.schema.ActionSchemaLoader
import ruleengine.schema.FieldSchemaLoader
import ui.actions.ActionSchemaYamlBridge
import ui.builder.BuilderRule
import ui.builder.BuilderRulesController
import ui.builder.RuleAstToBuilderMapper
import ui.diagrams.DiagramSurface
import ui.diagrams.FieldFlowDiagram
import ui.diagrams.OutcomeMapDiagram
import ui.diagrams.TraceDiagram
import ui.editor.CodeEditing
import ui.editor.rules.RuleEditorState
import ui.editor.rules.RuleValidationOutcome
import ui.editor.rules.RuleValidationRunner
import ui.editor.rules.StatusKind
import ui.editor.rules.sections.DiagnosticsSection
import ui.editor.rules.sections.StatusBarSection
import ui.editor.rules.sections.TopBarSection
import ui.manifest.ManifestYamlBridge
import ui.project.LinkedFileHeader
import ui.project.ProjectDialogHost
import ui.project.ProjectFileKind
import ui.project.ProjectWorkspace
import ui.project.toEditorState
import ui.samples.SampleGalleryScreen
import ui.samples.loadSample
import ui.schema.FieldSchemaYamlBridge
import ui.settings.SettingsController
import ui.settings.SettingsPersistence
import ui.settings.SettingsScreen
import ui.tester.RuleTestController
import ui.tester.RuleTestPanel
import ui.tester.TestCenterPanel
import ui.util.Words
import ui.workbench.ActionsAreaScreen
import ui.workbench.AppAreaIconRail
import ui.workbench.CenterEditorPanel
import ui.workbench.DiagramModeHost
import ui.workbench.ManifestAreaScreen
import ui.workbench.RightPanelWithTabs
import ui.workbench.RuleWorkbenchScreen
import ui.workbench.RuleWorkbenchViewModel
import ui.workbench.SchemaAreaScreen
import ui.workbench.builderCatalogActionsFrom
import ui.workbench.builderCatalogFieldsFrom
import ui.workbench.catalogActionsFrom
import ui.workbench.catalogFieldsFrom
import ui.workbench.catalogRulesFrom
import ui.workbench.diagramDataFor
import ui.workbench.inspector.InspectorPanel
import ui.workbench.model.AppArea
import ui.workbench.model.RuleWorkbenchState
import ui.workbench.model.WorkbenchAction
import ui.workbench.ruleTreeFilesFrom
import ui.workbench.toViewMode
import ui.workbench.uiDiagnosticsFrom

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
actual fun RuleEditor(closeController: AppCloseController) {
    val scope = rememberCoroutineScope()

    // Root workbench state (navigation, selection, panel tabs).
    val workbenchViewModel = remember {
        RuleWorkbenchViewModel(initialState = RuleWorkbenchState.Empty)
    }
    val workbenchState by workbenchViewModel.state.collectAsState()

    // Centralized state container for the editor content (text, parsed schema, diagnostics).
    val state = remember { RuleEditorState(scope = scope) }

    // Owns the project on disk: open, new, save, and linking shared schema/action files.
    val workspace = remember { ProjectWorkspace(state = state) }

    DisposableEffect(key1 = closeController, key2 = workspace) {
        closeController.onCloseRequested = workspace::requestClose
        onDispose { closeController.onCloseRequested = null }
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

    // ── Track word + DSL context on every cursor move ─────────────────────────
    LaunchedEffect(key1 = state.ruleValue.value.text, key2 = state.ruleValue.value.selection.start) {
        val cursor = state.ruleValue.value.selection.start
        val (wordStart, word) = Words.currentWord(text = state.ruleValue.value.text, cursorPos = cursor)
        state.autoCompleteWordStart.value = wordStart
        state.autoCompleteWord.value = word
        state.autoCompleteIndex.value = 0

        val ctx = analyzeDslContext(
            text = state.ruleValue.value.text,
            cursorPos = cursor,
            schema = state.parsedSchema.value,
        )
        state.dslContext.value = ctx

        // Never offered on its own. Once open it stays anchored to the word it was opened for, so
        // typing narrows it; it closes only when the caret leaves that word.
        if (state.showAutoComplete.value && !CodeEditing.isAnchorLive(
                text = state.ruleValue.value.text,
                cursor = cursor,
                anchor = state.autoCompleteAnchor.value,
            )
        ) {
            state.showAutoComplete.value = false
        }
    }

    // ── Debounced auto-validation ──────────────────────────────────────────────
    LaunchedEffect(key1 = state.ruleValue.value.text) {
        if (state.ruleValue.value.text.isBlank()) {
            state.diagnosticsList.value = emptyList()
            state.diagnosticsText.value = ""
            return@LaunchedEffect
        }
        delay(timeMillis = 700)
        // The guard stays a return from the effect, not from a lambda: with no schema there is
        // nothing to validate against and the previous diagnostics must be left as they are.
        val schema = state.parsedSchema.value ?: return@LaunchedEffect

        when (
            val outcome = RuleValidationRunner.run(
                ruleText = state.ruleValue.value.text,
                schema = schema,
                actions = state.parsedActionSchema.value,
            )
        ) {
            is RuleValidationOutcome.Completed -> {
                state.diagnosticsList.value = outcome.diagnostics
                state.diagnosticsText.value = if (outcome.isValid) "No issues found" else ""
                state.setStatus(
                    msg = if (outcome.isValid) "✓ Validation passed" else "✗ ${outcome.diagnostics.size} issue(s)",
                    kind = if (outcome.isValid) StatusKind.SUCCESS else StatusKind.ERROR,
                )
            }
            // Deliberately silent: this pass runs while the author is still typing, so a parse
            // failure is the normal state of half-written text, not something to report.
            is RuleValidationOutcome.Threw -> Unit
        }
    }

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
    // Honours "All files" like the inline canvas does, so expanding a view does not silently
    // narrow it back to the open file.
    val expandedDiagramRules = remember(
        key1 = state.ruleValue.value.text,
        key2 = state.showAllRules.value,
        key3 = state.allRulesText.value,
    ) {
        val text = if (state.showAllRules.value) state.allRulesText.value else state.ruleValue.value.text
        runCatching { Parser(input = text).parseRules() }.getOrElse { emptyList() }
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
    val selectedBuilderRuleId by builderRules.selectedId

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
    val catalogFields = remember(key1 = state.parsedSchema.value) {
        catalogFieldsFrom(schema = state.parsedSchema.value)
    }
    val builderCatalogFields = remember(key1 = state.parsedSchema.value) {
        builderCatalogFieldsFrom(schema = state.parsedSchema.value)
    }
    val catalogActions = remember(key1 = state.parsedActionSchema.value) {
        catalogActionsFrom(actions = state.parsedActionSchema.value)
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

    ProjectDialogHost(workspace = workspace)

    RuleWorkbenchScreen(
        topBar = {
            TopBarSection(
                workspace = workspace,
                onManageEntries = {
                    workbenchViewModel.dispatch(
                        action = WorkbenchAction.SelectAppArea(area = AppArea.MANIFEST),
                    )
                },
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
                AppArea.RULES -> CenterEditorPanel(
                    state = state,
                    scope = scope,
                    ruleMode = workbenchState.ruleMode,
                    onRuleModeChange = { mode ->
                        workbenchViewModel.dispatch(action = WorkbenchAction.SelectRuleMode(mode = mode))
                    },
                    builderEditorState = activeBuilderEditorState,
                    allRuleIds = builderStateMap.keys.filter { it.isNotBlank() },
                    allBuilderRules = allBuilderRules,
                    catalogRules = catalogRules,
                    onRuleSelected = { ruleId -> builderRules.select(ruleId = ruleId) },
                    onRenameRule = { oldId, newId -> builderRules.rename(oldId = oldId, newId = newId) },
                    onAddRule = { builderRules.add() },
                    catalogFields = builderCatalogFields,
                    catalogActions = builderCatalogActions,
                    onBuilderDslChange = { newDsl ->
                        builderRules.applyDsl(ruleId = activeBuilderEditorState.ruleId, newDsl = newDsl)
                    },
                    onConditionSelected = { conditionId ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectCondition(conditionId = conditionId),
                        )
                    },
                    ruleTreeFiles = ruleTreeFiles,
                    onTreeRuleSelected = { relativePath, ruleId ->
                        // The file load stays here: it is disk I/O against the editor's manifest state,
                        // and it has to happen before the selection is parked as pending.
                        if (relativePath == state.selectedManifestRuleFile.value || relativePath == "current") {
                            builderRules.select(ruleId = ruleId)
                        } else {
                            state.loadSingleManifestRuleFile(relativePath)
                            builderRules.selectWhenParsed(ruleId = ruleId)
                        }
                    },
                    testContent = {
                        TestCenterPanel(
                            state = testInputState,
                            onStateChange = { testInputState = it },
                            onRunTest = {
                                testController.run(
                                    ruleText = if (state.showAllRules.value) {
                                        state.allRulesText.value
                                    } else {
                                        state.ruleValue.value.text
                                    },
                                )
                            },
                            onLoadJson = { testController.loadInputJson() },
                            ruleIds = catalogRules.map { it.id },
                            ruleSelectionEnabled = !state.showAllRules.value,
                            runEnabled = state.parsedSchema.value != null
                                    && (state.ruleValue.value.text.isNotBlank() || state.showAllRules.value && state.allRulesText.value.isNotBlank())
                                    && !hasErrors,
                            runReason = when {
                                state.parsedSchema.value == null -> "Load a field schema first"
                                !state.showAllRules.value && state.ruleValue.value.text.isBlank() -> "Enter at least one rule"
                                hasErrors -> "Fix rule validation errors before running"
                                else -> null
                            },
                            traceContent = { results -> TraceDiagram(results = results) },
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SCHEMA -> Column(modifier = Modifier.fillMaxSize()) {
                    LinkedFileHeader(
                        label = "SCHEMA FILE",
                        linkedPath = workspace.session.value?.schemaLink,
                        isMissing = workspace.session.value?.missing(kind = ProjectFileKind.SCHEMA) != null,
                        onLink = workspace::linkSchema,
                        onUnlink = { workspace.unlink(kind = ProjectFileKind.SCHEMA) },
                    )
                    Spacer(modifier = Modifier.height(height = 10.dp))
                    SchemaAreaScreen(
                    schemaYaml = state.schemaText.value,
                    fromYaml = { yaml ->
                        FieldSchemaYamlBridge.fromYaml(yaml = yaml)
                    },
                    toYaml = { editorState ->
                        FieldSchemaYamlBridge.toYaml(state = editorState)
                    },
                    onSchemaYamlChange = { newYaml ->
                        state.schemaText.value = newYaml
                        state.schemaFieldValue.value = TextFieldValue(text = newYaml)
                        state.parsedSchema.value = runCatching {
                            FieldSchemaLoader.loadFromString(
                                content = newYaml,
                                nameHint = "schema",
                            )
                        }.getOrNull()
                    },
                    modifier = Modifier.fillMaxSize(),
                    // The same field-flow diagram the rule editor shows, filling the "Usages" tab
                    // that has been a placeholder: here the schema is the subject, so the fields
                    // nothing reads are the point.
                    usagesContent = {
                        DiagramSurface {
                            FieldFlowDiagram(
                                rules = expandedDiagramRules,
                                schema = state.parsedSchema.value,
                                entryWide = state.showAllRules.value,
                            )
                        }
                    },
                    yamlEditor = { value, onValueChange, editorModifier ->
                        YamlEditor(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = editorModifier,
                            editorType = YamlEditorType.FIELD_SCHEMA,
                            annotate = { text ->
                                annotateYaml(
                                    text = text,
                                    editorType = YamlEditorType.FIELD_SCHEMA,
                                )
                            },
                            buildCompletions = { context ->
                                buildYamlCompletions(
                                    context = context,
                                    editorType = YamlEditorType.FIELD_SCHEMA,
                                )
                            },
                        )
                    },
                    )
                }

                AppArea.ACTIONS -> Column(modifier = Modifier.fillMaxSize()) {
                    LinkedFileHeader(
                        label = "ACTIONS FILE",
                        linkedPath = workspace.session.value?.actionsLink,
                        isMissing = workspace.session.value?.missing(kind = ProjectFileKind.ACTIONS) != null,
                        onLink = workspace::linkActions,
                        onUnlink = { workspace.unlink(kind = ProjectFileKind.ACTIONS) },
                    )
                    Spacer(modifier = Modifier.height(height = 10.dp))
                    ActionsAreaScreen(
                    actionsYaml = state.actionSchemaText.value,
                    fromYaml = { yaml ->
                        ActionSchemaYamlBridge.fromYaml(yaml = yaml)
                    },
                    toYaml = { editorState ->
                        ActionSchemaYamlBridge.toYaml(state = editorState)
                    },
                    onActionsYamlChange = { newYaml ->
                        state.actionSchemaText.value = newYaml
                        state.parsedActionSchema.value = runCatching {
                            ActionSchemaLoader.loadFromString(content = newYaml)
                        }.getOrNull()
                    },
                    modifier = Modifier.fillMaxSize(),
                    // The outcome map rather than the field flow: in the actions area the question is
                    // which rules emit an action and which of them share an output bucket.
                    usagesContent = {
                        DiagramSurface {
                            OutcomeMapDiagram(rules = expandedDiagramRules)
                        }
                    },
                    yamlEditor = { value, onValueChange, editorModifier ->
                        YamlEditor(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = editorModifier,
                            editorType = YamlEditorType.ACTION_SCHEMA,
                            annotate = { text ->
                                annotateYaml(
                                    text = text,
                                    editorType = YamlEditorType.ACTION_SCHEMA,
                                )
                            },
                            buildCompletions = { context ->
                                buildYamlCompletions(
                                    context = context,
                                    editorType = YamlEditorType.ACTION_SCHEMA,
                                )
                            },
                        )
                    },
                    )
                }

                // The session is the manifest: edits here go straight onto it rather than into a
                // text buffer the saver would regenerate over.
                AppArea.MANIFEST -> ManifestAreaScreen(
                    state = workspace.session.value?.toEditorState()
                        ?: ManifestYamlBridge.fromYaml(yaml = state.manifestText.value),
                    onStateChange = { edited -> workspace.applyManifestEditorState(edited = edited) },
                    activeEntryId = workspace.session.value?.activeEntryId,
                    onSelectEntry = { entryId -> workspace.selectEntry(entryId = entryId) },
                    onAddEntry = { workspace.addEntry(entryId = workspace.suggestEntryId()) },
                    onRemoveEntry = { entryId -> workspace.requestRemoveEntry(entryId = entryId) },
                    fromYaml = { yaml ->
                        ManifestYamlBridge.fromYaml(yaml = yaml)
                    },
                    toYaml = { editorState ->
                        ManifestYamlBridge.toYaml(state = editorState)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SAMPLES -> SampleGalleryScreen(
                    onSampleSelected = { descriptor ->
                        scope.launch {
                            val loaded = loadSample(descriptor)
                            state.schemaText.value = loaded.schemaYaml
                            state.schemaFieldValue.value = TextFieldValue(text = loaded.schemaYaml)
                            state.parsedSchema.value = runCatching {
                                FieldSchemaLoader.loadFromString(
                                    content = loaded.schemaYaml,
                                    nameHint = descriptor.id,
                                )
                            }.getOrNull()
                            state.actionSchemaText.value = loaded.actionsYaml
                            state.actionFieldValue.value = TextFieldValue(text = loaded.actionsYaml)
                            state.parsedActionSchema.value = runCatching {
                                ActionSchemaLoader.loadFromString(content = loaded.actionsYaml)
                            }.getOrNull()
                            state.ruleValue.value = TextFieldValue(text = loaded.rulesText)
                            state.diagnosticsList.value = emptyList()
                            state.diagnosticsText.value = ""
                            state.setStatus(
                                msg = "Loaded sample: ${descriptor.name}",
                                kind = StatusKind.SUCCESS,
                            )
                            workbenchViewModel.dispatch(
                                action = WorkbenchAction.SelectAppArea(area = AppArea.RULES),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

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
            RightPanelWithTabs(
                tab = workbenchState.rightPanelTab,
                onTabChange = { tab ->
                    workbenchViewModel.dispatch(action = WorkbenchAction.SelectRightPanelTab(tab = tab))
                },
                inspectorContent = {
                    InspectorPanel(
                        selectedItem = workbenchState.selectedInspectorItem,
                        fields = catalogFields,
                        actions = catalogActions,
                        rules = catalogRules,
                        builderState = activeBuilderEditorState,
                        diagnostics = uiDiagnostics,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                simulateContent = {
                    RuleTestPanel(
                        state = testInputState,
                        onJsonChange = { testInputState = testInputState.copy(inputJson = it) },
                        onRunTest = { testController.run(ruleText = state.ruleValue.value.text) },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                expanded = state.rightPanelExpanded.value,
                onToggleExpanded = { state.rightPanelExpanded.value = !state.rightPanelExpanded.value },
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
        Window(
            onCloseRequest = { state.showExpandedDiagram.value = false },
            title = "Rule Diagram — Full View",
            state = rememberWindowState(size = DpSize(width = 1400.dp, height = 900.dp)),
        ) {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Bg,
                ) {
                    DiagramModeHost(
                        view = state.diagramView.value,
                        data = diagramDataFor(state = state, rules = expandedDiagramRules),
                    )
                }
            }
        }
    }
}
