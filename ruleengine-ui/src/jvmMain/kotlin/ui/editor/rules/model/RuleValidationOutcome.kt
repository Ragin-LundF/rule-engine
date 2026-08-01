package ui.editor.rules.model

import ruleengine.core.errors.ValidationDiagnostic

/**
 * What one parse-and-validate pass produced.
 *
 * A throw is a result, not an error to propagate: the parser rejects half-typed text constantly
 * while someone is editing, and each caller has its own idea of what to do about that — the
 * debounced pass ignores it, the Validate button reports it.
 */
internal sealed interface RuleValidationOutcome {

    /** The rules parsed and were validated. [isValid] is false when any diagnostic is an error. */
    data class Completed(
        val isValid: Boolean,
        val diagnostics: List<ValidationDiagnostic>,
    ) : RuleValidationOutcome

    /** Parsing or validation threw — most often incomplete text mid-edit. */
    data class Threw(val cause: Throwable) : RuleValidationOutcome
}
