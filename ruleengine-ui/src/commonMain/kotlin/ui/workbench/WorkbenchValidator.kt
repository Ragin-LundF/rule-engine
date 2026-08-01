package ui.workbench

import ui.workbench.model.WorkbenchValidationResult
/**
 * Platform-agnostic interface for running rule validation.
 * The implementation lives in jvmMain because it calls core APIs that depend on
 * JVM types (e.g. java.nio.file.Path).
 */
interface WorkbenchValidator {
    /**
     * Validate the given rule DSL text against the provided schema and optional
     * action schema texts.  Returns a [WorkbenchValidationResult] that is always
     * safe to display in the UI — no exceptions are propagated.
     */
    fun validate(
        schemaText: String,
        actionsText: String,
        ruleText: String,
    ): WorkbenchValidationResult
}
