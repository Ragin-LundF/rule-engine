package ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.editor.rules.RuleEditorState
import ui.manifest.model.ManifestEditorState
import ui.manifest.model.ManifestPathKind
import ui.tester.RuleTestController
import ui.tester.RuleTestPanel
import ui.tester.model.TestInputState
import ui.workbench.inspector.InspectorPanel
import ui.workbench.model.InspectorItem
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.catalog.CatalogField
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.mode.RightPanelTab

/**
 * The right panel: Inspector and Simulate, plus the collapse toggle.
 *
 * Simulate shares [testController] with the centre Test mode rather than owning a second one — both
 * run the same thing and have to agree about whether a run is in progress.
 *
 * [uiDiagnostics] arrives already scoped to the inspected rule; see `inspectorDiagnostics` in
 * `RuleEditor`, which owns the buffer the line ranges are measured against.
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
    ruleStates: Map<String, BuilderEditorState>,
    /** The builder's own field catalog: dotted paths plus the `$name` variables in scope. */
    builderFields: BuilderCatalog,
    onBuilderDslChange: (String) -> Unit,
    onBuilderMessage: (String) -> Unit,
    onSelectInspectorItem: (InspectorItem) -> Unit,
    /** The manifest as the session has it, and where an Inspector edit to it goes. */
    manifestState: ManifestEditorState?,
    onManifestChange: (ManifestEditorState) -> Unit,
    activeEntryId: String?,
    /** The loaded schema's top-level field types, for the scope picker. */
    schemaFieldTypes: Map<String, String>?,
    chooseManifestPath: ((ManifestPathKind) -> String?)?,
    chooseManifestPathDisabledReason: String?,
    uiDiagnostics: List<UiDiagnostic>,
    testInputState: TestInputState,
    onTestInputStateChange: (TestInputState) -> Unit,
    testController: RuleTestController,
    onToggleExpanded: () -> Unit,
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
                ruleStates = ruleStates,
                diagnostics = uiDiagnostics,
                builderFields = builderFields,
                onBuilderDslChange = onBuilderDslChange,
                onBuilderMessage = onBuilderMessage,
                onSelectItem = onSelectInspectorItem,
                // The Inspector is the writer of all three models. It reads the same objects the area
                // panels draw, so the two cannot disagree about what the schema currently is.
                schemaState = state.schemaEditor.state,
                onSchemaChange = { edited -> state.schemaEditor.state = edited },
                actionState = state.actionEditor.state,
                onActionChange = { edited -> state.actionEditor.state = edited },
                manifestState = manifestState,
                onManifestChange = onManifestChange,
                activeEntryId = activeEntryId,
                schemaFieldTypes = schemaFieldTypes,
                chooseManifestPath = chooseManifestPath,
                chooseManifestPathDisabledReason = chooseManifestPathDisabledReason,
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
        // Passed in rather than flipped here: the open state is persisted, and one writer is what
        // keeps the stored value from drifting away from the one on screen.
        onToggleExpanded = onToggleExpanded,
    )
}
