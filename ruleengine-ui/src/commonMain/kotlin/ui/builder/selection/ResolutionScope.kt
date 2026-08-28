package ui.builder.selection

import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.BuilderCatalog

/**
 * Where in the schema a selected element sits — the two facts a path lookup needs and a
 * [SelectionStep][ui.builder.model.selection.SelectionStep] list on its own does not carry.
 *
 * Both were being lost on the way down, and both produced the same symptom: the inspector reporting a
 * declared field as undeclared, and offering nothing in the dropdown beside it.
 *
 * [catalog] is which names resolve here at all. It is the document's catalog almost everywhere, and
 * the *element's* catalog inside a `where` — the engine resolves a filter against the collection
 * element with the document behind it (`ValueExpressionCompiler.elementSchema`), which
 * `OperandRules.filterCatalog` mirrors. Handing a filter's operands the document catalog is what made
 * `orders[month in …]` report `month` as undeclared.
 *
 * [prefix] is the segments that come *before* the selected one. A path segment opened on its own is
 * still the third segment of its path, and resolving it against the schema's top-level fields — which
 * is what an empty prefix means — is what made every non-root segment report as undeclared.
 */
data class ResolutionScope(
    val catalog: BuilderCatalog,
    val prefix: List<BuilderPathStep> = emptyList(),
) {
    /** The same scope pointed at a fresh path, which is what entering an operand starts. */
    fun atRoot(): ResolutionScope = if (prefix.isEmpty()) this else copy(prefix = emptyList())

    companion object {
        /** The scope of a rule with no schema loaded. */
        val Empty: ResolutionScope = ResolutionScope(catalog = BuilderCatalog.Empty)
    }
}
