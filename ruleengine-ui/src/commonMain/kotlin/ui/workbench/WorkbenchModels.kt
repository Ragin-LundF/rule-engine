package ui.workbench

/**
 * High-level application area selected in the left icon rail.
 */
enum class AppArea {
    RULES,
    SCHEMA,
    ACTIONS,
    MANIFEST,
    SAMPLES,
    SETTINGS,
}

/**
 * Center-panel modes available inside the [AppArea.RULES] area.
 */
enum class RuleMode {
    BUILDER,
    CODE,
    DIAGRAM,
    TEST,
    TABLE,
}

/**
 * Center-panel modes available inside the [AppArea.SCHEMA] area.
 */
enum class SchemaMode {
    VISUAL,
    YAML,
    USAGES,
}

/**
 * Center-panel modes available inside the [AppArea.ACTIONS] area.
 */
enum class ActionMode {
    VISUAL,
    YAML,
    USAGES,
}

/**
 * Center-panel modes available inside the [AppArea.MANIFEST] area.
 */
enum class ManifestMode {
    BUILDER,
    YAML,
    CHECKS,
}

/**
 * Tab displayed in the right inspector/simulate panel.
 */
enum class RightPanelTab {
    INSPECTOR,
    SIMULATE,
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
    /** A field definition from the field schema. */
    data class Field(val id: String) : InspectorItem

    /** An action definition from the action schema. */
    data class Action(val name: String) : InspectorItem

    /** A parsed rule. */
    data class Rule(val id: String) : InspectorItem

    /** A single condition row inside Builder mode. */
    data class Condition(val conditionId: String) : InspectorItem

    /** A manifest project. */
    data class Manifest(val name: String) : InspectorItem
}
