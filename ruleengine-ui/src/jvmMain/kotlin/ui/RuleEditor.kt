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
import kotlinx.coroutines.delay
import ruleengine.core.errors.Severity
import ruleengine.dsl.parser.Parser
import ui.builder.BuilderRule
import ui.builder.BuilderRulesController
import ui.builder.RuleAstToBuilderMapper
import ui.editor.CodeEditing
import ui.editor.rules.RuleEditorState
import ui.editor.rules.RuleValidationOutcome
import ui.editor.rules.RuleValidationRunner
import ui.editor.rules.StatusKind
import ui.editor.rules.sections.DiagnosticsSection
import ui.editor.rules.sections.StatusBarSection
import ui.editor.rules.sections.TopBarSection
import ui.project.ProjectDialogHost
import ui.project.ProjectWorkspace
import ui.settings.SettingsController
import ui.settings.SettingsPersistence
import ui.settings.SettingsScreen
import ui.tester.RuleTestController
import ui.util.Words
import ui.workbench.ActionsAreaContent
import ui.workbench.AppAreaIconRail
import ui.workbench.ExpandedDiagramWindow
import ui.workbench.ManifestAreaContent
import ui.workbench.RuleWorkbenchScreen
import ui.workbench.RuleWorkbenchViewModel
import ui.workbench.RulesAreaContent
import ui.workbench.SamplesAreaContent
import ui.workbench.SchemaAreaContent
import ui.workbench.WorkbenchRightPanel
import ui.workbench.builderCatalogActionsFrom
import ui.workbench.builderCatalogFieldsFrom
import ui.workbench.catalogActionsFrom
import ui.workbench.catalogFieldsFrom
import ui.workbench.catalogRulesFrom
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
                    catalogFields = builderCatalogFields,
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
                )

                AppArea.ACTIONS -> ActionsAreaContent(
                    state = state,
                    workspace = workspace,
                    expandedDiagramRules = expandedDiagramRules,
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.MANIFEST -> ManifestAreaContent(
                    state = state,
                    workspace = workspace,
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SAMPLES -> SamplesAreaContent(
                    state = state,
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
                onTabChange = { tab ->
                    workbenchViewModel.dispatch(action = WorkbenchAction.SelectRightPanelTab(tab = tab))
                },
                selectedInspectorItem = workbenchState.selectedInspectorItem,
                catalogFields = catalogFields,
                catalogActions = catalogActions,
                catalogRules = catalogRules,
                builderEditorState = activeBuilderEditorState,
                uiDiagnostics = uiDiagnostics,
                testInputState = testInputState,
                onTestInputStateChange = { testInputState = it },
                testController = testController,
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
