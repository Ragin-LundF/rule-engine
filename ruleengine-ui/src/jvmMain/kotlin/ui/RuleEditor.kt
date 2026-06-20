package ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import ruleengine.compiler.Validator
import ruleengine.dsl.parser.Parser
import ruleengine.manifest.ManifestLoader
import ruleengine.schema.FieldSchemaLoader
import ui.builder.BuilderEditorState
import ui.builder.BuilderRule
import ui.builder.CatalogActionInfo
import ui.builder.CatalogFieldInfo
import ui.builder.RuleAstToBuilderMapper
import ui.components.PlaceholderPanel
import ui.schema.FieldSchemaYamlBridge
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.editor.rules.isContextuallyImmediate
import ui.editor.rules.sections.DiagnosticsSection
import ui.editor.rules.sections.StatusBarSection
import ui.editor.rules.sections.TopBarSection
import ui.tester.JvmRuleSimulationService
import ui.tester.RuleTestPanel
import ui.tester.TestInputState
import ui.workbench.ActionsAreaPlaceholder
import ui.workbench.AppArea
import ui.workbench.AppAreaIconRail
import ui.workbench.CatalogAction
import ui.workbench.CatalogField
import ui.workbench.CatalogRule
import ui.workbench.CatalogRuleStatus
import ui.workbench.CenterEditorPanel
import ui.workbench.InspectorPanel
import ui.workbench.JvmWorkbenchValidator
import ui.workbench.ManifestAreaPlaceholder
import ui.workbench.RightPanelTab
import ui.workbench.RightPanelWithTabs
import ui.workbench.RuleMode
import ui.workbench.RuleWorkbenchScreen
import ui.workbench.RuleWorkbenchState
import ui.workbench.RuleWorkbenchViewModel
import ui.workbench.SchemaAreaScreen
import ui.workbench.UiDiagnostic
import ui.workbench.UiDiagnosticSeverity
import ui.workbench.WorkbenchAction
import ui.workbench.toRuleMode
import ui.workbench.toViewMode

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
actual fun RuleEditor() {
    val scope = rememberCoroutineScope()

    // Root workbench state (navigation, selection, panel tabs).
    val validator = remember { JvmWorkbenchValidator() }
    val workbenchViewModel = remember {
        RuleWorkbenchViewModel(
            validator = validator,
            scope = scope,
            initialState = RuleWorkbenchState.Empty,
        )
    }
    val workbenchState by workbenchViewModel.state.collectAsState()

    // Centralized state container for the editor content (text, parsed schema, diagnostics).
    val state = remember { RuleEditorState(scope = scope) }

    // Keep the legacy editor view mode in sync with the workbench rule mode.
    LaunchedEffect(key1 = workbenchState.ruleMode) {
        state.viewMode.value = workbenchState.ruleMode.toViewMode()
    }

    // ── Auto-load first manifest entry when manifest is newly set ─────────────
    LaunchedEffect(key1 = state.parsedManifest.value) {
        val manifest = state.parsedManifest.value ?: run {
            state.selectedManifestEntry.value = null
            return@LaunchedEffect
        }
        val first = manifest.entries.firstOrNull() ?: return@LaunchedEffect
        // Only auto-load when no entry is already selected (prevents unwanted override
        // when the same manifest is re-parsed after a text edit).
        if (state.selectedManifestEntry.value == null) {
            state.loadManifestEntry(entry = first)
        }
    }

    // ── Track word + DSL context on every cursor move ─────────────────────────
    LaunchedEffect(key1 = state.ruleValue.value.text, key2 = state.ruleValue.value.selection.start) {
        val cursor = state.ruleValue.value.selection.start
        val (wordStart, word) = extractCurrentWord(text = state.ruleValue.value.text, cursorPos = cursor)
        state.autoCompleteWordStart.value = wordStart
        state.autoCompleteWord.value = word
        state.autoCompleteIndex.value = 0

        val ctx = analyzeDslContext(
            text = state.ruleValue.value.text,
            cursorPos = cursor,
            schema = state.parsedSchema.value,
        )
        state.dslContext.value = ctx

        val lastChar = if (cursor > 0) state.ruleValue.value.text.getOrNull(index = cursor - 1) else null
        val afterSpace = lastChar == ' ' || lastChar == '\n'
        state.showAutoComplete.value = state.autoCompleteWord.value.isNotEmpty() ||
                (afterSpace && isContextuallyImmediate(context = ctx))
    }

    // ── Debounced auto-validation ──────────────────────────────────────────────
    LaunchedEffect(key1 = state.ruleValue.value.text) {
        if (state.ruleValue.value.text.isBlank()) {
            state.diagnosticsList.value = emptyList()
            state.diagnosticsText.value = ""
            return@LaunchedEffect
        }
        delay(timeMillis = 700)
        runCatching {
            if (state.parsedSchema.value == null) return@LaunchedEffect
            val asts = Parser(input = state.ruleValue.value.text).parseRules()
            val result = Validator.validate(
                asts = asts,
                schema = state.parsedSchema.value!!,
                actions = state.parsedActionSchema.value,
            )
            state.diagnosticsList.value = result.diagnostics
            state.diagnosticsText.value = if (result.isValid) "No issues found" else ""
            state.setStatus(
                msg = if (result.isValid) "✓ Validation passed" else "✗ ${result.diagnostics.size} issue(s)",
                kind = if (result.isValid) StatusKind.SUCCESS else StatusKind.ERROR,
            )
        }
    }

    // ── Test panel state ──────────────────────────────────────────────────────
    var testInputState by remember { mutableStateOf(TestInputState.Empty) }
    val simulationService = remember { JvmRuleSimulationService() }

    // ── Parsed rules for the expanded diagram window ───────────────────────────
    val diagramRulesForWindow = remember(key1 = state.ruleValue.value.text) {
        runCatching { Parser(input = state.ruleValue.value.text).parseRules() }.getOrElse { emptyList() }
    }

    // ── Builder rule derived from the first parsed rule AST ─────────────────────
    val builderRule = remember(key1 = diagramRulesForWindow, key2 = state.selectedManifestEntry.value) {
        val selectedId = state.selectedManifestEntry.value
        val ast = if (selectedId != null) {
            diagramRulesForWindow.firstOrNull { it.id == selectedId } ?: diagramRulesForWindow.firstOrNull()
        } else {
            diagramRulesForWindow.firstOrNull()
        }
        if (ast != null) RuleAstToBuilderMapper.map(ast) else BuilderRule.None
    }
    val builderEditorState = remember(key1 = builderRule) {
        BuilderEditorState.fromBuilderRule(builderRule)
    }

    // ── Catalog data derived from parsed schema/actions/rules ─────────────────
    val catalogFields = remember(key1 = state.parsedSchema.value) {
        state.parsedSchema.value?.fields?.values?.map { def ->
            CatalogField(
                id = def.id.value,
                type = def.type.name.lowercase(),
                operators = def.operators.map { it.value },
                normalizers = def.normalizers.map { it.value },
                alias = def.alias,
            )
        } ?: emptyList()
    }
    val builderCatalogFields = remember(key1 = state.parsedSchema.value) {
        state.parsedSchema.value?.fields?.values?.map { def ->
            CatalogFieldInfo(
                id = def.id.value,
                type = def.type.name.lowercase(),
                operators = def.operators.map { it.value },
            )
        } ?: emptyList()
    }
    val catalogActions = remember(key1 = state.parsedActionSchema.value) {
        state.parsedActionSchema.value?.actions?.values?.map { def ->
            CatalogAction(
                name = def.name,
                argType = def.argTypes.joinToString { it.name.lowercase() },
            )
        } ?: emptyList()
    }
    val builderCatalogActions = remember(key1 = state.parsedActionSchema.value) {
        state.parsedActionSchema.value?.actions?.values?.map { def ->
            CatalogActionInfo(
                name = def.name,
                argType = def.argTypes.firstOrNull()?.name?.lowercase() ?: "string",
            )
        } ?: emptyList()
    }
    val hasErrors = state.diagnosticsList.value.any { it.severity == ruleengine.core.errors.Severity.ERROR }
    val uiDiagnostics = remember(key1 = state.diagnosticsList.value) {
        state.diagnosticsList.value.map { diagnostic ->
            UiDiagnostic(
                severity = when (diagnostic.severity) {
                    ruleengine.core.errors.Severity.ERROR -> UiDiagnosticSeverity.ERROR
                    ruleengine.core.errors.Severity.WARNING -> UiDiagnosticSeverity.WARNING
                    ruleengine.core.errors.Severity.INFO -> UiDiagnosticSeverity.INFO
                },
                message = diagnostic.message,
                line = diagnostic.line,
                column = diagnostic.column,
            )
        }
    }
    val catalogRules = remember(key1 = diagramRulesForWindow, key2 = hasErrors) {
        diagramRulesForWindow.map { ast ->
            CatalogRule(
                id = ast.id,
                status = when {
                    hasErrors -> CatalogRuleStatus.INVALID
                    state.diagnosticsList.value.isEmpty() && state.ruleValue.value.text.isNotBlank() -> CatalogRuleStatus.VALID
                    else -> CatalogRuleStatus.DRAFT
                },
            )
        }
    }

    RuleWorkbenchScreen(
        topBar = { TopBarSection(state = state, scope = scope) },
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
                    builderEditorState = builderEditorState,
                    catalogFields = builderCatalogFields,
                    catalogActions = builderCatalogActions,
                    onBuilderDslChange = { newDsl ->
                        state.ruleValue.value = TextFieldValue(text = newDsl)
                    },
                    onConditionSelected = { conditionId ->
                        workbenchViewModel.dispatch(
                            action = WorkbenchAction.SelectCondition(conditionId = conditionId),
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                AppArea.SCHEMA -> SchemaAreaScreen(
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
                )
                AppArea.ACTIONS -> ActionsAreaPlaceholder(modifier = Modifier.fillMaxSize())
                AppArea.MANIFEST -> ManifestAreaPlaceholder(modifier = Modifier.fillMaxSize())
                AppArea.SAMPLES, AppArea.SETTINGS -> PlaceholderPanel(
                    label = workbenchState.appArea.name,
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
                        builderState = builderEditorState,
                        diagnostics = uiDiagnostics,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                simulateContent = {
                    RuleTestPanel(
                        state = testInputState,
                        onJsonChange = { testInputState = testInputState.copy(inputJson = it) },
                        onRunTest = {
                            scope.launch {
                                testInputState = testInputState.copy(isRunning = true)
                                val result = simulationService.simulate(
                                    schemaText = state.schemaText.value,
                                    actionsText = state.actionSchemaText.value,
                                    ruleText = state.ruleValue.value.text,
                                    ruleId = state.selectedManifestEntry.value ?: "",
                                    inputJson = testInputState.inputJson,
                                )
                                testInputState = testInputState.copy(
                                    isRunning = false,
                                    outcome = result.outcome,
                                    traceRows = result.traceRows,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        },
        bottomBar = {
            DiagnosticsSection(state = state)
            StatusBarSection(state = state)
        },
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
                    RuleDiagramView(rules = diagramRulesForWindow)
                }
            }
        }
    }
}
