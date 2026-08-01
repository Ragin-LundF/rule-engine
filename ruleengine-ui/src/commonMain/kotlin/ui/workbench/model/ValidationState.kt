package ui.workbench.model
/**
 * Overall validation state of the current rule text.
 */
enum class ValidationState {
    /** No validation has been run yet. */
    IDLE,
    /** Validation is currently running. */
    VALIDATING,
    /** Validation completed with no errors. */
    VALID,
    /** Validation completed and at least one error was found. */
    INVALID,
}
