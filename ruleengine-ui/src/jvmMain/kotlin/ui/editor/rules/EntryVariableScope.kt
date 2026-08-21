package ui.editor.rules

import ruleengine.core.analysis.VariableUsage
import ruleengine.dsl.ast.AssignmentKindAst
import ui.diagrams.model.RuleSource

/**
 * The variables an entry publishes *before* one of its rule files, mapped to the clause that writes
 * each one.
 *
 * The engine validates a manifest entry as one unit, so a `$name` read in the last rule file resolves
 * against the `set` and `add` clauses of every file listed before it. The editor holds one file at a
 * time, so without this the same rules that load cleanly in the engine are reported as reading unknown
 * variables — which is exactly what happened to a final "assessment" file whose whole job is to read
 * what the files before it accumulated.
 *
 * [files] must be in manifest order, which is what makes "before" mean anything. Nothing is inherited
 * when [openPath] is not one of them: a buffer holding the whole entry already contains its own
 * writers, and a file the manifest does not list has no position to be before or after.
 */
internal fun inheritedVariablesBefore(
    openPath: String?,
    files: List<RuleSource>,
): Map<String, AssignmentKindAst> {
    if (openPath == null) {
        return emptyMap()
    }
    val openIndex = files.indexOfFirst { source -> source.relativePath == openPath }
    if (openIndex <= 0) {
        return emptyMap()
    }

    val kinds = LinkedHashMap<String, AssignmentKindAst>()
    for (source in files.take(n = openIndex)) {
        for (rule in source.rules) {
            for ((name, kind) in VariableUsage.writeKindsOf(rule = rule)) {
                // First writer wins, matching the kind the validator records and checks later writes
                // against. A name written by both clauses across files is still reported there.
                kinds.putIfAbsent(name, kind)
            }
        }
    }
    return kinds
}

/**
 * The inherited scope for whatever the editor currently has open.
 *
 * Reads through [RuleEditorState.parsedRuleFilesForCurrentEntryWithOpenBuffer] so an unsaved `set` or
 * `add` in another file of the entry counts as soon as it is typed, the same way the Builder's operand
 * picker sees it.
 */
internal fun RuleEditorState.inheritedVariablesForOpenBuffer(): Map<String, AssignmentKindAst> {
    return inheritedVariablesBefore(
        openPath = selectedManifestRuleFile.value,
        files = parsedRuleFilesForCurrentEntryWithOpenBuffer(),
    )
}
