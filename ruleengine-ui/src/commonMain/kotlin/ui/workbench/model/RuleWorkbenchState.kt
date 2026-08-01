package ui.workbench.model

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
