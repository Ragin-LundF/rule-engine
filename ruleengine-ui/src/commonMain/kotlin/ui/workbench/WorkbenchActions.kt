package ui.workbench

/**
 * Sealed hierarchy of all user-initiated actions that can mutate [RuleWorkbenchState].
 * Each action carries only the data needed to produce the next state.
 * No parsing or validation logic lives here.
 */
sealed interface WorkbenchAction {

    /** Switch the active application area in the left icon rail. */
    data class SelectAppArea(val area: AppArea) : WorkbenchAction

    /** Switch the active mode inside the [AppArea.RULES] area. */
    data class SelectRuleMode(val mode: RuleMode) : WorkbenchAction

    /** Switch the active mode inside the [AppArea.SCHEMA] area. */
    data class SelectSchemaMode(val mode: SchemaMode) : WorkbenchAction

    /** Switch the active mode inside the [AppArea.ACTIONS] area. */
    data class SelectActionMode(val mode: ActionMode) : WorkbenchAction

    /** Switch the active mode inside the [AppArea.MANIFEST] area. */
    data class SelectManifestMode(val mode: ManifestMode) : WorkbenchAction

    /** Switch the active tab in the right panel. */
    data class SelectRightPanelTab(val tab: RightPanelTab) : WorkbenchAction

    /** Select a rule by its identifier in the rule list panel. */
    data class SelectRule(val ruleId: String?) : WorkbenchAction

    /** Select a field by its identifier. */
    data class SelectField(val fieldId: String?) : WorkbenchAction

    /** Select an action by its name. */
    data class SelectAction(val actionName: String?) : WorkbenchAction

    /** Select a condition row by its identifier in Builder mode. */
    data class SelectCondition(val conditionId: String) : WorkbenchAction

    /** Select an item in the inspector panel (field, action, rule, condition, or manifest). */
    data class SelectInspectorItem(val item: InspectorItem?) : WorkbenchAction

    /** Request a validation run against the current schema and rule text. */
    data object RequestValidation : WorkbenchAction

    /** Apply a completed validation result to the state. */
    data class ApplyValidationResult(
        val diagnostics: List<UiDiagnostic>,
        val validationState: ValidationState,
    ) : WorkbenchAction
}
