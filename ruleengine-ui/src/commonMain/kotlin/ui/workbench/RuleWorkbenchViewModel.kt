package ui.workbench

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ui.workbench.model.InspectorItem
import ui.workbench.model.RuleWorkbenchState
import ui.workbench.model.WorkbenchAction

/**
 * Shared view model for the rule workbench.
 *
 * Holds an immutable [RuleWorkbenchState] snapshot and processes [WorkbenchAction]s to produce the
 * next state. It is navigation and selection only: validation lives in
 * `ui.editor.rules.RuleValidationRunner`, and the diagnostics it produces are rendered from
 * `RuleEditorState`, not from here.
 */
class RuleWorkbenchViewModel(
    initialState: RuleWorkbenchState = RuleWorkbenchState.Empty,
) {
    private val _state = MutableStateFlow(value = initialState)
    val state: StateFlow<RuleWorkbenchState> = _state.asStateFlow()

    /**
     * Dispatch a [WorkbenchAction] and update state accordingly.
     *
     * Suppressed at 15 against a threshold of 14: this is one exhaustive `when` over a sealed
     * hierarchy, and every branch is a single `copy`. Detekt counts a branch as a decision, so the
     * measure grows with the number of actions rather than with any tangling. Splitting it would
     * mean two `when`s with an `else`, which is what would actually make it unsafe — the
     * exhaustiveness is what turns deleting an action subtype into a compile error rather than a
     * silently ignored dispatch.
     */
    @Suppress("CyclomaticComplexMethod")
    fun dispatch(action: WorkbenchAction) {
        when (action) {
            is WorkbenchAction.SelectAppArea -> updateState { it.copy(appArea = action.area) }
            is WorkbenchAction.SelectRuleMode -> updateState { it.copy(ruleMode = action.mode) }
            is WorkbenchAction.SelectSchemaMode -> updateState { it.copy(schemaMode = action.mode) }
            is WorkbenchAction.SelectActionMode -> updateState { it.copy(actionMode = action.mode) }
            is WorkbenchAction.SelectManifestMode -> updateState { it.copy(manifestMode = action.mode) }
            is WorkbenchAction.SelectRightPanelTab -> updateState { it.copy(rightPanelTab = action.tab) }
            is WorkbenchAction.SelectRule -> updateState {
                it.copy(
                    selectedRuleId = action.ruleId,
                    selectedInspectorItem = action.ruleId?.let { id -> InspectorItem.Rule(id = id) },
                )
            }
            is WorkbenchAction.SelectField -> updateState {
                it.copy(
                    selectedFieldId = action.fieldId,
                    selectedInspectorItem = action.fieldId?.let { id -> InspectorItem.Field(id = id) },
                )
            }
            is WorkbenchAction.SelectAction -> updateState {
                it.copy(
                    selectedActionName = action.actionName,
                    selectedInspectorItem = action.actionName?.let { name -> InspectorItem.Action(name = name) },
                )
            }
            is WorkbenchAction.SelectCondition -> updateState {
                it.copy(
                    selectedConditionId = action.conditionId,
                    selectedInspectorItem = InspectorItem.Condition(conditionId = action.conditionId),
                )
            }
            is WorkbenchAction.SelectInspectorItem -> updateState { it.copy(selectedInspectorItem = action.item) }
        }
    }

    /**
     * Update a slice of state via a synchronous transform.
     * Package-internal so tests can drive state transitions directly.
     */
    internal fun updateState(transform: (RuleWorkbenchState) -> RuleWorkbenchState) {
        _state.value = transform(_state.value)
    }
}
