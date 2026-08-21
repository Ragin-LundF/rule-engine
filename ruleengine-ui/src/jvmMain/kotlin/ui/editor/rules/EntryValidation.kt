package ui.editor.rules

import ruleengine.compiler.EntryValidator
import ruleengine.compiler.RuleFileAsts
import ruleengine.core.errors.ValidationDiagnostic
import ui.editor.rules.model.RuleValidationOutcome

/**
 * Validates every rule file of the open manifest entry, not just the file on screen.
 *
 * The engine validates an entry as one unit, so this is the only pass that can report what a single file
 * cannot see: a rule id repeated in another file, and — the reason it exists — a `$name` that resolves
 * only because an earlier file publishes it. The per-file pass behind
 * [RuleEditorState.inheritedVariablesForOpenBuffer] handles the second case for the open buffer alone;
 * this one covers the whole entry, which is what the Validate button should mean when a project is open.
 *
 * The open buffer stands in for the file it was loaded from, so unsaved edits are validated rather than
 * whatever is on disk. Returns null when there is no entry to validate — a loose rule file, or a sample
 * whose buffer already *is* the whole entry — and the caller falls back to the single-buffer pass.
 */
internal fun RuleEditorState.validateOpenEntry(): RuleValidationOutcome? {
    val schema = ruleSchema ?: return null
    val files = parsedRuleFilesForCurrentEntryWithOpenBuffer()
    if (selectedManifestRuleFile.value == null || files.size < 2) {
        return null
    }

    return runCatching {
        val result = EntryValidator.validate(
            files = files.map { source -> RuleFileAsts(path = source.relativePath, asts = source.rules) },
            schema = schema,
            actions = parsedActionSchema.value,
        )
        RuleValidationOutcome.Completed(isValid = result.isValid, diagnostics = result.diagnostics)
    }.getOrElse { cause -> RuleValidationOutcome.Threw(cause = cause) }
}

/**
 * Whether this diagnostic is about the file the editor is showing.
 *
 * A diagnostic with no file came from a pass that validated one buffer and had nothing to attribute it
 * to, so it belongs to whatever is open. This is what keeps another file's line 12 from underlining
 * line 12 of the file on screen.
 */
internal fun ValidationDiagnostic.isAbout(openFile: String?): Boolean {
    val attributed = file?.toString() ?: return true
    return attributed == openFile
}
