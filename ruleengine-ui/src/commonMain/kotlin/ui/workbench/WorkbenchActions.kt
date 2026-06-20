package ui.workbench

/**
 * Sealed hierarchy of all user-initiated actions that can mutate [WorkbenchState].
 * Each action carries only the data needed to produce the next state.
 * No parsing or validation logic lives here.
 */
sealed interface WorkbenchAction {

    /** Switch the active authoring mode (Builder, Code, Diagram, Test, Table). */
    data class SelectMode(val mode: WorkbenchMode) : WorkbenchAction

    /** Replace the full rule DSL text in the editor. */
    data class UpdateRuleText(val text: String) : WorkbenchAction

    /** Replace the field schema YAML/JSON text. */
    data class UpdateSchemaText(val text: String) : WorkbenchAction

    /** Replace the action schema YAML/JSON text. */
    data class UpdateActionsText(val text: String) : WorkbenchAction

    /** Select a rule by its identifier in the rule list panel. */
    data class SelectRule(val ruleId: String?) : WorkbenchAction

    /** Select an item in the inspector panel (field, action, or rule). */
    data class SelectInspectorItem(val item: InspectorItem?) : WorkbenchAction

    /** Request a validation run against the current schema and rule text. */
    data object RequestValidation : WorkbenchAction

    /** Apply a completed validation result to the state. */
    data class ApplyValidationResult(
        val diagnostics: List<UiDiagnostic>,
        val validationState: ValidationState,
    ) : WorkbenchAction
}
