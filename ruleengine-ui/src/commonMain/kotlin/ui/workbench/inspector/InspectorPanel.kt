package ui.workbench.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableConditionNode
import ui.components.SectionTitle
import ui.workbench.model.InspectorItem
import ui.workbench.model.UiDiagnostic
import ui.workbench.model.catalog.CatalogField
import ui.workbench.model.catalog.CatalogRule

/**
 * Top-level inspector panel that delegates to the appropriate sub-inspector
 * based on the currently selected [InspectorItem].
 * When nothing is selected, shows a placeholder prompt.
 */
@Composable
fun InspectorPanel(
    selectedItem: InspectorItem?,
    fields: List<CatalogField>,
    actions: List<CatalogActionInfo>,
    rules: List<CatalogRule>,
    builderState: BuilderEditorState? = null,
    diagnostics: List<UiDiagnostic> = emptyList(),
    modifier: Modifier = Modifier,
) {
    when (selectedItem) {
        is InspectorItem.Field -> Inspect(
            subject = fields.firstOrNull { it.id == selectedItem.id },
            modifier = modifier,
        ) { field -> FieldInspector(field = field, modifier = modifier) }

        is InspectorItem.Action -> Inspect(
            subject = actions.firstOrNull { it.name == selectedItem.name },
            modifier = modifier,
        ) { action -> ActionInspector(action = action, modifier = modifier) }

        is InspectorItem.Rule -> Inspect(
            subject = rules.firstOrNull { it.id == selectedItem.id },
            modifier = modifier,
        ) { rule ->
            RuleInspector(
                rule = rule,
                conditionCount = builderState?.let { countLeafConditions(it.conditionNodes) } ?: 0,
                actionCount = builderState?.actions?.size ?: 0,
                variableNames = builderState?.variables?.map { it.name }.orEmpty(),
                diagnostics = diagnostics,
                modifier = modifier,
            )
        }

        is InspectorItem.Condition -> Inspect(
            subject = builderState?.let { findLeafCondition(it.conditionNodes, selectedItem.conditionId) },
            modifier = modifier,
        ) { condition -> ConditionInspector(condition = condition.toImmutable(), modifier = modifier) }

        is InspectorItem.Manifest -> ManifestInspector(modifier = modifier)
        null -> InspectorPlaceholder(modifier = modifier)
    }
}

/**
 * Renders [content] for [subject], or the placeholder when there is no subject.
 *
 * Selection outlives the catalog it points into — a rule can be selected and then edited away — so
 * every lookup here can miss, and each branch used to repeat the same null check.
 */
@Suppress("FunctionNaming")
@Composable
private fun <T : Any> Inspect(subject: T?, modifier: Modifier, content: @Composable (T) -> Unit) {
    if (subject == null) InspectorPlaceholder(modifier = modifier) else content(subject)
}

/**
 * Recursively counts all leaf conditions in the node tree.
 */
private fun countLeafConditions(nodes: List<MutableConditionNode>): Int {
    return nodes.sumOf { node ->
        when (node) {
            is MutableConditionNode.Leaf -> 1
            is MutableConditionNode.ComparisonLeaf -> 1
            is MutableConditionNode.Group -> countLeafConditions(node.nodes)
        }
    }
}

/**
 * Recursively finds a leaf condition by id in the node tree.
 */
private fun findLeafCondition(
    nodes: List<MutableConditionNode>,
    id: String,
): MutableBuilderCondition? {
    for (node in nodes) {
        when (node) {
            is MutableConditionNode.Leaf -> {
                if (node.inner.id == id) return node.inner
            }
            // Comparison rows are inspected in the row itself, not in the inspector panel.
            is MutableConditionNode.ComparisonLeaf -> Unit
            is MutableConditionNode.Group -> {
                val found = findLeafCondition(node.nodes, id)
                if (found != null) return found
            }
        }
    }
    return null
}

@Composable
private fun InspectorPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle(text = "INSPECTOR")
        Text(
            text = "Select a field, action, rule, or condition to see details.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}

/**
 * A simple two-column label/value row used by all sub-inspectors.
 */
@Composable
internal fun InspectorRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(0.65f),
        )
    }
}
