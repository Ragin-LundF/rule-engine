package ui.workbench

/**
 * Authoring modes available in the rule workbench.
 * DIAGRAM is intentionally omitted here because it is a view-only mode
 * managed separately; it is not a primary editing mode.
 */
enum class WorkbenchMode {
    BUILDER,
    CODE,
    DIAGRAM,
    TABLE,
    TEST,
}

/**
 * UI-facing severity level for diagnostics.
 * Mirrors core Severity without exposing the JVM-only core type to commonMain.
 */
enum class UiDiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
}

/**
 * A single UI-facing diagnostic message produced by validation.
 * Does not reference java.nio.file.Path so it is safe in commonMain.
 */
data class UiDiagnostic(
    val severity: UiDiagnosticSeverity,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val suggestion: String? = null,
)

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

/**
 * An item selected in the inspector panel.
 */
sealed interface InspectorItem {
    data class Field(val id: String) : InspectorItem
    data class Action(val name: String) : InspectorItem
    data class Rule(val id: String) : InspectorItem
}
