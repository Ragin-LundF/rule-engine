package ui.workbench

/**
 * Immutable snapshot of the entire rule workbench UI state.
 * All fields are plain Kotlin types safe for commonMain.
 */
data class WorkbenchState(
    val selectedMode: WorkbenchMode = WorkbenchMode.CODE,
    val schemaText: String = "",
    val actionsText: String = "",
    val ruleText: String = "",
    val selectedRuleId: String? = null,
    val selectedInspectorItem: InspectorItem? = null,
    val diagnostics: List<UiDiagnostic> = emptyList(),
    val validationState: ValidationState = ValidationState.IDLE,
) {
    companion object {
        val Empty: WorkbenchState = WorkbenchState()
    }
}
