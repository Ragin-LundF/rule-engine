package ui.workbench.model

import ui.workbench.model.mode.ActionMode
import ui.workbench.model.mode.AppArea
import ui.workbench.model.mode.ManifestMode
import ui.workbench.model.mode.RightPanelTab
import ui.workbench.model.mode.RuleMode
import ui.workbench.model.mode.SchemaMode

/**
 * Immutable snapshot of the entire rule workbench UI state.
 * All fields are plain Kotlin types safe for commonMain.
 */
data class RuleWorkbenchState(
    val appArea: AppArea = AppArea.RULES,
    val ruleMode: RuleMode = RuleMode.CODE,
    val schemaMode: SchemaMode = SchemaMode.VISUAL,
    val actionMode: ActionMode = ActionMode.VISUAL,
    val manifestMode: ManifestMode = ManifestMode.BUILDER,
    val selectedRuleId: String? = null,
    val selectedFieldId: String? = null,
    val selectedActionName: String? = null,
    val selectedConditionId: String? = null,
    val selectedInspectorItem: InspectorItem? = null,
    val rightPanelTab: RightPanelTab = RightPanelTab.INSPECTOR,
) {
    companion object {
        /** Empty initial state. */
        val Empty: RuleWorkbenchState = RuleWorkbenchState()
    }
}
