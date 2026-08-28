package ui.workbench.areas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import ruleengine.core.domain.dto.RuleBranch
import ui.AccentRed
import ui.TextSecondary
import ui.builder.BuilderRulesController
import ui.builder.BuilderToRuleDsl
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.selection.SelectionStep
import ui.builder.selection.SelectionResolver
import ui.copyToClipboard
import ui.diagrams.TraceDiagram
import ui.dock.CanvasDockScaffold
import ui.dock.DockController
import ui.dock.DockHighlight
import ui.dock.DockHighlightKind
import ui.dock.EditorDock
import ui.dock.fileDockTab
import ui.dock.model.DockBadge
import ui.dock.model.DockBadgeKind
import ui.dock.model.DockSurface
import ui.dock.model.DockTab
import ui.dsl.annotateRule
import ui.editor.rules.RuleEditorState
import ui.findRuleBlockRange
import ui.rowLineRanges
import ui.tester.RuleTestController
import ui.tester.TestCenterPanel
import ui.tester.model.TestInputState
import ui.workbench.CenterEditorPanel
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.RuleTreeFile
import ui.workbench.model.mode.RuleMode

/**
 * The Rules area: the centre editor in whichever mode is active, and the Test panel it hosts.
 *
 * [builderEditorState] is passed in rather than read from [builderRules] here on purpose — the
 * screen reads it during *its* composition, un-`remember`ed, and moving that read down would change
 * which composable recomposes when the map changes. Same reason for [allRuleIds].
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun RulesAreaContent(
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleMode: RuleMode,
    onRuleModeChange: (RuleMode) -> Unit,
    builderRules: BuilderRulesController,
    builderEditorState: BuilderEditorState,
    allBuilderRules: List<BuilderRule>,
    catalogRules: List<CatalogRule>,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    onBuilderMessage: (String) -> Unit,
    selectedNodeId: String?,
    selectedStatementId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
    testInputState: TestInputState,
    onTestInputStateChange: (TestInputState) -> Unit,
    testController: RuleTestController,
    hasErrors: Boolean,
    dock: DockController,
    modifier: Modifier = Modifier,
) {
    // The dock belongs to the two visual canvases. Code mode *is* the text, so previewing it there
    // would be the same panel twice; the diagram, test and table modes have no file of their own.
    val showsDock = ruleMode == RuleMode.BUILDER || ruleMode == RuleMode.BOARD

    // One slot, used with or without a dock under it. Spelling the call twice is how the two copies
    // drift apart on the next argument either of them gains.
    val centre: @Composable () -> Unit = {
        RulesCenter(
            state = state,
            scope = scope,
            ruleMode = ruleMode,
            onRuleModeChange = onRuleModeChange,
            builderRules = builderRules,
            builderEditorState = builderEditorState,
            allBuilderRules = allBuilderRules,
            catalogRules = catalogRules,
            catalogActions = catalogActions,
            ruleTreeFiles = ruleTreeFiles,
            onBuilderMessage = onBuilderMessage,
            selectedNodeId = selectedNodeId,
            selectedStatementId = selectedStatementId,
            selectedSteps = selectedSteps,
            onSelectNode = onSelectNode,
            onSelectStatement = onSelectStatement,
            testInputState = testInputState,
            onTestInputStateChange = onTestInputStateChange,
            testController = testController,
            hasErrors = hasErrors,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (!showsDock) {
        Box(modifier = modifier.fillMaxSize()) { centre() }
        return
    }

    val expanded = dock.isExpanded(surface = DockSurface.RULES)
    CanvasDockScaffold(
        expanded = expanded,
        dockHeight = dock.heightDp.dp,
        onDockResize = if (expanded) {
            { delta: Dp, available: Dp ->
                dock.setHeight(value = dock.heightDp + delta.value, ceiling = available.value)
            }
        } else {
            null
        },
        onDockResetHeight = { dock.resetHeight() },
        modifier = modifier,
        dock = {
            RulesDock(
                state = state,
                builderEditorState = builderEditorState,
                selectedNodeId = selectedNodeId,
                dock = dock,
                expanded = expanded,
            )
        },
        canvas = centre,
    )
}

/** The Rules dock, wired to the one controller. */
@Suppress("FunctionNaming")
@Composable
private fun RulesDock(
    state: RuleEditorState,
    builderEditorState: BuilderEditorState,
    selectedNodeId: String?,
    dock: DockController,
    expanded: Boolean,
) {
    EditorDock(
        tabs = ruleDockTabs(
            state = state,
            builderEditorState = builderEditorState,
            selectedNodeId = selectedNodeId,
        ),
        selectedTabId = dock.selectedTab(surface = DockSurface.RULES),
        onSelectTab = { tabId -> dock.selectTab(surface = DockSurface.RULES, tabId = tabId) },
        expanded = expanded,
        onToggleExpanded = { dock.toggleExpanded(surface = DockSurface.RULES) },
        onCopy = { copyToClipboard(text = state.ruleValue.value.text) },
    )
}

/**
 * The Rules dock: the whole rule file, and what is wrong with it.
 *
 * The *file*, not the generated rule. The generated text was what the old dock showed, and it answered
 * "what will this rule become" while leaving "and where does it sit" unanswered — so the rule the
 * author was editing appeared to be the only thing in the file. Here the file is the text and the open
 * rule is a highlight inside it, which answers both.
 */
@Suppress("FunctionNaming")
@Composable
private fun ruleDockTabs(
    state: RuleEditorState,
    builderEditorState: BuilderEditorState,
    selectedNodeId: String?,
): List<DockTab> {
    val fullText = state.ruleValue.value.text
    val ruleId = builderEditorState.ruleId

    // The open rule as context, and the selected row as focus inside it. `rowLineRanges` searches only
    // within the block, so an identical row in a neighbouring rule cannot light up alongside it.
    val block = if (ruleId.isBlank()) null else findRuleBlockRange(fullText = fullText, ruleId = ruleId)
    val rowText = selectedNodeId
        ?.let { id -> SelectionResolver.findNode(nodes = builderEditorState.conditionNodes, id = id) }
        ?.let { node -> BuilderToRuleDsl.renderRow(node = node) }
    val highlights = buildList {
        block?.let { range -> add(element = DockHighlight(range = range, kind = DockHighlightKind.CONTEXT)) }
        if (block != null && rowText != null) {
            rowLineRanges(fullText = fullText, block = block, rowText = rowText).forEach { range ->
                add(element = DockHighlight(range = range, kind = DockHighlightKind.FOCUS))
            }
        }
    }

    // The open file's problems, not this rule's: a diagnostic carries a file and a line but no rule id,
    // and the Builder does not know where in the file its rule starts. Showing the file's is honest and
    // still useful; claiming per-rule precision would not be.
    val problems = state.diagnosticsList.value.map { diagnostic ->
        val where = diagnostic.line?.let { line -> "line $line: " }.orEmpty()
        where + diagnostic.message
    }

    val schema = state.ruleSchema
    val actions = state.parsedActionSchema.value
    val diagnostics = state.diagnosticsList.value

    return listOf(
        fileDockTab(
            title = state.selectedManifestRuleFile.value?.substringAfterLast(delimiter = '/') ?: "rules",
            text = fullText,
            annotate = { text ->
                annotateRule(text = text, schema = schema, actions = actions, diagnostics = diagnostics)
            },
            highlights = highlights,
        ),
        DockTab(
            id = "checks",
            title = "Checks",
            badge = if (problems.isEmpty()) {
                DockBadge(text = "ok", kind = DockBadgeKind.OK)
            } else {
                DockBadge(text = problems.size.toString(), kind = DockBadgeKind.ERROR)
            },
        ) {
            DockProblemList(problems = problems)
        },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun DockProblemList(problems: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(state = rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        if (problems.isEmpty()) {
            Text(text = "No problems in this file.", style = MaterialTheme.typography.caption, color = TextSecondary)
        }
        problems.forEach { problem ->
            Text(text = problem, style = MaterialTheme.typography.caption, color = AccentRed)
        }
    }
}

/** The centre panel, factored out so it can be composed with or without a dock under it. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RulesCenter(
    state: RuleEditorState,
    scope: CoroutineScope,
    ruleMode: RuleMode,
    onRuleModeChange: (RuleMode) -> Unit,
    builderRules: BuilderRulesController,
    builderEditorState: BuilderEditorState,
    allBuilderRules: List<BuilderRule>,
    catalogRules: List<CatalogRule>,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    onBuilderMessage: (String) -> Unit,
    selectedNodeId: String?,
    selectedStatementId: String?,
    selectedSteps: List<SelectionStep>?,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onSelectStatement: (RuleBranch, String) -> Unit,
    testInputState: TestInputState,
    onTestInputStateChange: (TestInputState) -> Unit,
    testController: RuleTestController,
    hasErrors: Boolean,
    modifier: Modifier = Modifier,
) {
    CenterEditorPanel(
        state = state,
        scope = scope,
        ruleMode = ruleMode,
        onRuleModeChange = onRuleModeChange,
        builderEditorState = builderEditorState,
        allBuilderRules = allBuilderRules,
        catalogRules = catalogRules,
        onRuleSelected = { ruleId -> builderRules.select(ruleId = ruleId) },
        onRenameRule = { oldId, newId -> builderRules.rename(oldId = oldId, newId = newId) },
        onAddRule = { builderRules.add() },
        catalogActions = catalogActions,
        onBuilderDslChange = { newDsl ->
            builderRules.applyDsl(ruleId = builderEditorState.ruleId, newDsl = newDsl)
        },
        onBuilderMessage = onBuilderMessage,
        selectedNodeId = selectedNodeId,
        selectedStatementId = selectedStatementId,
        selectedSteps = selectedSteps,
        onSelectNode = onSelectNode,
        onSelectStatement = onSelectStatement,
        ruleTreeFiles = ruleTreeFiles,
        onTreeRuleSelected = { relativePath, ruleId ->
            // The file load stays at this level: it is disk I/O against the editor's manifest state,
            // and it has to happen before the selection is parked as pending.
            when {
                // In "All files" the buffer already holds every rule of the entry, so there is
                // nothing to load — and loading one file would replace the buffer with a snapshot of
                // that file alone, throwing away every unsaved edit in the other ones. This is the
                // path a sample takes, which opens in All files with no single file selected.
                state.showAllRules.value -> builderRules.select(ruleId = ruleId)

                relativePath == state.selectedManifestRuleFile.value || relativePath == "current" ->
                    builderRules.select(ruleId = ruleId)

                else -> {
                    state.loadSingleManifestRuleFile(relativePath)
                    builderRules.selectWhenParsed(ruleId = ruleId)
                }
            }
        },
        testContent = {
            RulesTestContent(
                state = state,
                testInputState = testInputState,
                onTestInputStateChange = onTestInputStateChange,
                testController = testController,
                catalogRules = catalogRules,
                hasErrors = hasErrors,
            )
        },
        modifier = modifier,
    )
}

/**
 * The Test tab inside the Rules area.
 *
 * [runReason] and [TestCenterPanel.runEnabled] are derived from the same three facts in the same
 * order, so the button's disabled state and its tooltip can never disagree.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RulesTestContent(
    state: RuleEditorState,
    testInputState: TestInputState,
    onTestInputStateChange: (TestInputState) -> Unit,
    testController: RuleTestController,
    catalogRules: List<CatalogRule>,
    hasErrors: Boolean,
) {
    TestCenterPanel(
        state = testInputState,
        onStateChange = onTestInputStateChange,
        onRunTest = {
            // The buffer the Builder and the code editor edit, so a run always tests what is on
            // screen rather than a copy taken when the files were loaded.
            testController.run(ruleText = state.ruleValue.value.text)
        },
        onLoadJson = { testController.loadInputJson() },
        ruleIds = catalogRules.map { it.id },
        ruleSelectionEnabled = !state.showAllRules.value,
        runEnabled = state.parsedSchema.value != null &&
                state.ruleValue.value.text.isNotBlank() &&
                !hasErrors,
        runReason = when {
            state.parsedSchema.value == null -> "Load a field schema first"
            !state.showAllRules.value && state.ruleValue.value.text.isBlank() -> "Enter at least one rule"
            hasErrors -> "Fix rule validation errors before running"
            else -> null
        },
        traceContent = { results -> TraceDiagram(results = results) },
    )
}
