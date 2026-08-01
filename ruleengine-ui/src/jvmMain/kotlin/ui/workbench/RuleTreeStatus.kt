package ui.workbench

import ruleengine.core.errors.Severity
import ruleengine.core.errors.ValidationDiagnostic
import ui.workbench.model.CatalogRuleStatus

/**
 * Status shown by a rule tree row.
 *
 * A rule counts as invalid either when a validation error explicitly names its id, or — for
 * rules belonging to the file currently open in the editor — whenever the buffer has any error
 * at all, since a parse-level error rarely names every rule it invalidates. Rules in other files
 * cannot use that fallback: their errors, if any, belong to a different read of the file that has
 * not been checked here, so only a message that names the rule id is trustworthy for them.
 */
internal fun ruleTreeStatusFor(
    ruleId: String,
    description: String?,
    relativePath: String,
    currentFile: String?,
    diagnostics: List<ValidationDiagnostic>,
): CatalogRuleStatus {
    val namesThisRule = diagnostics.any { it.severity == Severity.ERROR && it.message.contains(ruleId) }
    val currentFileHasErrors = relativePath == currentFile && diagnostics.any { it.severity == Severity.ERROR }
    return when {
        namesThisRule || currentFileHasErrors -> CatalogRuleStatus.INVALID
        description.isNullOrBlank() -> CatalogRuleStatus.DRAFT
        else -> CatalogRuleStatus.VALID
    }
}
