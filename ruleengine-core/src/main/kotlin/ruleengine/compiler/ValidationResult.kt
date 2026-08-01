package ruleengine.compiler

import ruleengine.core.errors.ValidationDiagnostic

/**
 * The outcome of validating a rule set: whether it may be compiled, and everything worth telling the
 * author either way.
 *
 * [isValid] is false only when at least one diagnostic is an error — warnings do not block compilation.
 */
data class ValidationResult(val isValid: Boolean, val diagnostics: List<ValidationDiagnostic>)
