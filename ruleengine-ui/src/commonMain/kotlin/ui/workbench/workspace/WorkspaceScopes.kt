package ui.workbench.workspace

import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.fieldsAtPath
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.names
import ui.builder.selection.ResolutionScope
import ui.builder.selection.SelectionResolver
import ui.builder.selection.SelectionTarget
import ui.workbench.model.InspectorItem
import ui.workbench.model.workspace.ContextScope

/**
 * What a name can refer to at the current selection — the context column's "in scope here".
 *
 * Read from the same [ResolutionScope] the editor resolves against, so the list and the dropdowns in
 * the open editor cannot offer different names. Inside a `where`, that is the element's members; a bare
 * name there means a member, which is exactly the thing an author gets wrong without being shown it.
 */
object WorkspaceScopes {

    /** The variable prefix, which is how the catalog tells a published variable from a field. */
    private const val VARIABLE_PREFIX = "$"

    fun of(state: BuilderEditorState, item: InspectorItem?, catalog: BuilderCatalog): ContextScope {
        val scope = resolvedScope(state = state, item = item, catalog = catalog)
            ?: return recordScope(catalog = catalog)
        if (scope.catalog == catalog && scope.prefix.isEmpty()) {
            return recordScope(catalog = catalog)
        }
        // A segment is picked from the members at the end of the segments in front of it.
        val owner = scope.prefix.lastOrNull()?.name
        if (owner != null) {
            return ContextScope(
                title = "Inside $owner",
                fields = scope.catalog.fieldsAtPath(segments = scope.prefix.names),
                variables = emptyList(),
            )
        }
        // An element scope lays the element's members over the record's fields (see
        // `OperandRules.filterCatalog`). The members are what is new here; the record's fields are
        // still nameable, and the note under the list says so rather than repeating them.
        return ContextScope(
            title = "Inside each element",
            fields = scope.catalog.fields.filterNot { field -> field in catalog.fields || field.isVariable() },
            variables = emptyList(),
            note = "A name here means a member of the element first. The record's fields can still be named.",
        )
    }

    private fun recordScope(catalog: BuilderCatalog): ContextScope {
        val (variables, fields) = catalog.fields.partition { field -> field.isVariable() }
        return ContextScope(title = "Fields of the record", fields = fields, variables = variables)
    }

    private fun resolvedScope(
        state: BuilderEditorState,
        item: InspectorItem?,
        catalog: BuilderCatalog,
    ): ResolutionScope? {
        val target = when (item) {
            is InspectorItem.Condition -> SelectionResolver.resolveCondition(
                state = state,
                conditionId = item.conditionId,
                steps = item.steps,
                catalog = catalog,
            )

            is InspectorItem.Statement -> SelectionResolver.resolveStatement(
                state = state,
                branch = item.branch,
                statementId = item.statementId,
                steps = item.steps,
                catalog = catalog,
            )

            else -> null
        }
        return when (target) {
            is SelectionTarget.Operand -> target.scope
            is SelectionTarget.Segment -> target.scope
            is SelectionTarget.Filter -> target.scope
            is SelectionTarget.Extraction -> target.scope
            else -> null
        }
    }

    private fun CatalogFieldInfo.isVariable(): Boolean {
        return id.startsWith(prefix = VARIABLE_PREFIX)
    }
}
