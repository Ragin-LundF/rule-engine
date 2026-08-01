package ui.workbench.diagram

import ruleengine.dsl.ast.RuleAst
import ui.diagrams.model.DiagramData
import ui.editor.rules.RuleEditorState

/**
 * Gathers the slices of [RuleEditorState] the diagram views read into one [DiagramData].
 *
 * Lives here rather than in `ui.diagrams.model` so the model package stays free of the editor state,
 * and rather than inline in the canvas so the detached full-view window builds the same thing from
 * the same place.
 *
 * @param rules The rules already parsed for the current scope, passed in because the caller derives
 *   them from the live editor buffer and re-parsing here would double the work on every keystroke.
 */
fun diagramDataFor(state: RuleEditorState, rules: List<RuleAst>): DiagramData {
    val entryId = state.selectedManifestEntry.value
    val entry = state.parsedManifest.value?.entries?.find { candidate -> candidate.id == entryId }
    return DiagramData(
        rules = rules,
        sources = state.entryRuleSources.value,
        entryId = state.selectedManifestEntry.value,
        schemaPath = entry?.schema,
        actionsPath = entry?.actions,
        schema = state.parsedSchema.value,
        entryWide = state.showAllRules.value,
    )
}
