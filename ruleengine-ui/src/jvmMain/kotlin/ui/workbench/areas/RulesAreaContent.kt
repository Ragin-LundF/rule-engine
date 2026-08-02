package ui.workbench.areas

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import ui.builder.BuilderRulesController
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.diagrams.TraceDiagram
import ui.editor.rules.RuleEditorState
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
    allRuleIds: List<String>,
    allBuilderRules: List<BuilderRule>,
    catalogRules: List<CatalogRule>,
    catalogFields: List<CatalogFieldInfo>,
    catalogActions: List<CatalogActionInfo>,
    ruleTreeFiles: List<RuleTreeFile>,
    onConditionSelected: (String) -> Unit,
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
        allRuleIds = allRuleIds,
        allBuilderRules = allBuilderRules,
        catalogRules = catalogRules,
        onRuleSelected = { ruleId -> builderRules.select(ruleId = ruleId) },
        onRenameRule = { oldId, newId -> builderRules.rename(oldId = oldId, newId = newId) },
        onAddRule = { builderRules.add() },
        catalogFields = catalogFields,
        catalogActions = catalogActions,
        onBuilderDslChange = { newDsl ->
            builderRules.applyDsl(ruleId = builderEditorState.ruleId, newDsl = newDsl)
        },
        onConditionSelected = onConditionSelected,
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
