package ui.workbench.inspector

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.builder.inspector.BuilderInspector
import ui.builder.inspector.InspectorAnchor
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.workbench.model.InspectorItem

/**
 * The bridge from the workbench's selection to the builder's editing surface.
 *
 * Thin on purpose. `InspectorItem` is the workbench's notion of "what is selected"; `InspectorAnchor`
 * is the builder's. Translating here keeps the dependency pointing one way — the builder knows nothing
 * about the workbench — and leaves the panel to dispatch on the kind of selection rather than on the
 * shape of a rule.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun BuilderNodeInspector(
    item: InspectorItem,
    builderState: BuilderEditorState?,
    builderFields: BuilderCatalog,
    builderActions: List<CatalogActionInfo>,
    onSelectItem: (InspectorItem) -> Unit,
    onDslChange: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = builderState
    if (state == null || state.isLocked) {
        InspectorPlaceholder(modifier = modifier)
        return
    }

    when (item) {
        is InspectorItem.Condition -> BuilderInspector(
            state = state,
            anchor = InspectorAnchor.Condition(conditionId = item.conditionId),
            steps = item.steps,
            fields = builderFields,
            actions = builderActions,
            onSelect = { steps -> onSelectItem(item.copy(steps = steps)) },
            onSelectNode = { nodeId -> onSelectItem(InspectorItem.Condition(conditionId = nodeId)) },
            onDslChange = onDslChange,
            onMessage = onMessage,
            modifier = modifier,
        )

        is InspectorItem.Statement -> BuilderInspector(
            state = state,
            anchor = InspectorAnchor.Statement(branch = item.branch, statementId = item.statementId),
            steps = item.steps,
            fields = builderFields,
            actions = builderActions,
            onSelect = { steps -> onSelectItem(item.copy(steps = steps)) },
            onSelectNode = { nodeId -> onSelectItem(InspectorItem.Condition(conditionId = nodeId)) },
            onDslChange = onDslChange,
            onMessage = onMessage,
            modifier = modifier,
        )

        else -> InspectorPlaceholder(modifier = modifier)
    }
}
