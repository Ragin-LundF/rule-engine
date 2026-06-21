package ui.workbench

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared view model for the rule workbench.
 * Holds an immutable [RuleWorkbenchState] snapshot and processes [WorkbenchAction]s
 * to produce the next state. Validation is delegated to [WorkbenchValidator]
 * so that the view model itself is platform-agnostic and unit-testable.
 */
class RuleWorkbenchViewModel(
    private val validator: WorkbenchValidator,
    private val scope: CoroutineScope,
    initialState: RuleWorkbenchState = RuleWorkbenchState.Empty,
) {
    private val _state = MutableStateFlow(value = initialState)
    val state: StateFlow<RuleWorkbenchState> = _state.asStateFlow()

    /** Dispatch a [WorkbenchAction] and update state accordingly. */
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
            is WorkbenchAction.RequestValidation -> requestValidation()
            is WorkbenchAction.ApplyValidationResult -> updateState {
                it.copy(
                    diagnostics = action.diagnostics,
                    validationState = action.validationState,
                )
            }
        }
    }

    /**
     * Update a slice of state via a synchronous transform.
     * Package-internal so tests can drive state transitions directly.
     */
    internal fun updateState(transform: (RuleWorkbenchState) -> RuleWorkbenchState) {
        _state.value = transform(_state.value)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun requestValidation() {
        updateState { it.copy(validationState = ValidationState.VALIDATING) }
        scope.launch {
            val result = runCatching {
                validator.validate(
                    schemaText = "",
                    actionsText = "",
                    ruleText = "",
                )
            }.getOrElse { throwable ->
                WorkbenchValidationResult(
                    diagnostics = listOf(
                        UiDiagnostic(
                            severity = UiDiagnosticSeverity.ERROR,
                            message = throwable.message ?: "Unexpected validation error",
                        )
                    ),
                    validationState = ValidationState.INVALID,
                )
            }
            dispatch(
                action = WorkbenchAction.ApplyValidationResult(
                    diagnostics = result.diagnostics,
                    validationState = result.validationState,
                )
            )
        }
    }
}
