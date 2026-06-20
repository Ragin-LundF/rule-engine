package ui.workbench

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared view model for the rule workbench.
 * Holds an immutable [WorkbenchState] snapshot and processes [WorkbenchAction]s
 * to produce the next state.  Validation is delegated to [WorkbenchValidator]
 * so that the view model itself is platform-agnostic and unit-testable.
 */
class RuleWorkbenchViewModel(
    private val validator: WorkbenchValidator,
    private val scope: CoroutineScope,
    initialState: WorkbenchState = WorkbenchState.Empty,
) {
    private val _state = MutableStateFlow(value = initialState)
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    /** Dispatch a [WorkbenchAction] and update state accordingly. */
    fun dispatch(action: WorkbenchAction) {
        when (action) {
            is WorkbenchAction.SelectMode -> updateState { it.copy(selectedMode = action.mode) }
            is WorkbenchAction.UpdateRuleText -> updateState { it.copy(ruleText = action.text) }
            is WorkbenchAction.UpdateSchemaText -> updateState { it.copy(schemaText = action.text) }
            is WorkbenchAction.UpdateActionsText -> updateState { it.copy(actionsText = action.text) }
            is WorkbenchAction.SelectRule -> updateState { it.copy(selectedRuleId = action.ruleId) }
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun updateState(transform: (WorkbenchState) -> WorkbenchState) {
        _state.value = transform(_state.value)
    }

    private fun requestValidation() {
        updateState { it.copy(validationState = ValidationState.VALIDATING) }
        scope.launch {
            val current = _state.value
            val result = runCatching {
                validator.validate(
                    schemaText = current.schemaText,
                    actionsText = current.actionsText,
                    ruleText = current.ruleText,
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
