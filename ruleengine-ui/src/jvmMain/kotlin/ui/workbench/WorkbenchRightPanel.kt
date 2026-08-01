package ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.builder.BuilderEditorState
import ui.builder.CatalogActionInfo
import ui.editor.rules.RuleEditorState
import ui.tester.RuleTestController
import ui.tester.RuleTestPanel
import ui.tester.TestInputState
import ui.workbench.inspector.InspectorPanel
import ui.workbench.model.CatalogField
import ui.workbench.model.CatalogRule
import ui.workbench.model.InspectorItem
import ui.workbench.model.RightPanelTab
import ui.workbench.model.UiDiagnostic

/**
 * The right panel: Inspector and Simulate, plus the collapse toggle.
 *
 * Simulate shares [testController] with the centre Test mode rather than owning a second one — both
 * run the same thing and have to agree about whether a run is in progress.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun WorkbenchRightPanel(
    state: RuleEditorState,
    tab: RightPanelTab,
    onTabChange: (RightPanelTab) -> Unit,
    selectedInspectorItem: InspectorItem?,
    catalogFields: List<CatalogField>,
    catalogActions: List<CatalogActionInfo>,
    catalogRules: List<CatalogRule>,
    builderEditorState: BuilderEditorState,
    uiDiagnostics: List<UiDiagnostic>,
    testInputState: TestInputState,
    onTestInputStateChange: (TestInputState) -> Unit,
    testController: RuleTestController,
) {
    RightPanelWithTabs(
        tab = tab,
        onTabChange = onTabChange,
        inspectorContent = {
            InspectorPanel(
                selectedItem = selectedInspectorItem,
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
                onJsonChange = { onTestInputStateChange(testInputState.copy(inputJson = it)) },
                onRunTest = { testController.run(ruleText = state.ruleValue.value.text) },
                modifier = Modifier.fillMaxSize(),
            )
        },
        expanded = state.rightPanelExpanded.value,
        onToggleExpanded = { state.rightPanelExpanded.value = !state.rightPanelExpanded.value },
    )
}
