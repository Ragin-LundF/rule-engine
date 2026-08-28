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
import ui.actions.model.ActionEditorState
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.components.SectionTitle
import ui.manifest.model.ManifestEditorState
import ui.manifest.model.ManifestPathKind
import ui.schema.findByPath
import ui.schema.model.SchemaEditorState
import ui.schema.updateAtPath
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
    /**
     * One builder state per rule id, so the rule inspector describes the rule it names.
     *
     * Separate from [builderState] on purpose: the condition branch has to read the *open* builder
     * state, because that is where the condition being inspected lives, while the rule branch has to
     * read the *selected* rule's — in code mode the caret can sit in a rule the builder does not hold
     * open, and taking the counts from [builderState] there reports one rule's id with another rule's
     * conditions and actions.
     */
    ruleStates: Map<String, BuilderEditorState> = emptyMap(),
    diagnostics: List<UiDiagnostic> = emptyList(),
    /** The builder's own field catalog — dotted paths and `$name` variables, not the schema table's. */
    builderFields: BuilderCatalog = BuilderCatalog.Empty,
    /** Regenerated rule text, for the edits made in the builder branches. */
    onBuilderDslChange: (String) -> Unit = {},
    /** Where a refused builder gesture explains itself. */
    onBuilderMessage: (String) -> Unit = {},
    /** Moves the selection — how drilling into an operand works. */
    onSelectItem: (InspectorItem) -> Unit = {},
    /**
     * The schema being edited, and how to write it back.
     *
     * Null leaves the field branch read-only, which is what a caller with no schema editor gets. The
     * Inspector is the *only* writer of these models — see the sub-inspectors.
     */
    schemaState: SchemaEditorState? = null,
    onSchemaChange: (SchemaEditorState) -> Unit = {},
    actionState: ActionEditorState? = null,
    onActionChange: (ActionEditorState) -> Unit = {},
    manifestState: ManifestEditorState? = null,
    onManifestChange: (ManifestEditorState) -> Unit = {},
    /** Which manifest entry the project is saving against, so the inspector can say so. */
    activeEntryId: String? = null,
    /** The loaded schema's top-level field types, which is what a scope is checked against. */
    schemaFieldTypes: Map<String, String>? = null,
    /** Opens the platform file dialog for a manifest path; see `ManifestInspector.choosePath`. */
    chooseManifestPath: ((ManifestPathKind) -> String?)? = null,
    /** Why that dialog cannot be used — an unsaved project has nothing to be relative to. */
    chooseManifestPathDisabledReason: String? = null,
    modifier: Modifier = Modifier,
) {
    when (selectedItem) {
        // The dotted path, not a top-level id: a member of a collection is selectable, and a leaf name
        // is not unique — `lender` can belong to two different structures.
        is InspectorItem.Field -> FieldBranch(
            path = selectedItem.id,
            schemaState = schemaState,
            catalogUsages = fields.firstOrNull { candidate -> candidate.id == selectedItem.id }?.usages,
            onSchemaChange = onSchemaChange,
            onSelectItem = onSelectItem,
            modifier = modifier,
        )

        is InspectorItem.Action -> ActionBranch(
            name = selectedItem.name,
            actionState = actionState,
            catalogUsages = actions.firstOrNull { candidate -> candidate.name == selectedItem.name }?.usages,
            onActionChange = onActionChange,
            modifier = modifier,
        )

        is InspectorItem.Rule -> Inspect(
            subject = rules.firstOrNull { it.id == selectedItem.id },
            modifier = modifier,
        ) { rule ->
            val ruleState = ruleStates[rule.id]
            RuleInspector(
                rule = rule,
                conditionCount = ruleState?.let { countLeafConditions(it.conditionNodes) } ?: 0,
                actionCount = ruleState?.actions?.size ?: 0,
                elseActionCount = ruleState?.elseActions?.size ?: 0,
                notExistsActionCount = ruleState?.notExistsActions?.size ?: 0,
                variableNames = ruleState?.let { state ->
                    (state.variables + state.elseVariables + state.notExistsVariables).map { it.name }
                }.orEmpty(),
                diagnostics = diagnostics,
                modifier = modifier,
            )
        }

        // Everything selected inside a rule goes to one place, which is where the editing surface
        // is being built. See BuilderNodeInspector.
        is InspectorItem.Condition,
        is InspectorItem.Statement,
        -> BuilderNodeInspector(
            item = selectedItem,
            builderState = builderState,
            builderFields = builderFields,
            builderActions = actions,
            onSelectItem = onSelectItem,
            onDslChange = onBuilderDslChange,
            onMessage = onBuilderMessage,
            modifier = modifier,
        )

        is InspectorItem.Manifest -> ManifestBranch(
            manifestState = manifestState,
            onManifestChange = onManifestChange,
            activeEntryId = activeEntryId,
            schemaFieldTypes = schemaFieldTypes,
            choosePath = chooseManifestPath,
            choosePathDisabledReason = chooseManifestPathDisabledReason,
            modifier = modifier,
        )
        null -> InspectorPlaceholder(modifier = modifier)
    }
}



/** The manifest branch, extracted for the same reason [FieldBranch] was: this function has one job. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ManifestBranch(
    manifestState: ManifestEditorState?,
    onManifestChange: (ManifestEditorState) -> Unit,
    activeEntryId: String?,
    schemaFieldTypes: Map<String, String>?,
    choosePath: ((ManifestPathKind) -> String?)?,
    choosePathDisabledReason: String?,
    modifier: Modifier,
) {
    Inspect(subject = manifestState, modifier = modifier) { manifest ->
        ManifestInspector(
            manifest = manifest,
            activeEntryId = activeEntryId,
            fieldTypes = schemaFieldTypes,
            onManifestChange = onManifestChange,
            choosePath = choosePath,
            choosePathDisabledReason = choosePathDisabledReason,
            modifier = modifier,
        )
    }
}

/**
 * The field branch.
 *
 * Split out because the panel's `when` had grown past what one function should decide, and because this
 * is where the Inspector stopped being a summary: [onSchemaChange] below is the only writer of the
 * schema model.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun FieldBranch(
    path: String,
    schemaState: SchemaEditorState?,
    catalogUsages: Int?,
    onSchemaChange: (SchemaEditorState) -> Unit,
    onSelectItem: (InspectorItem) -> Unit,
    modifier: Modifier,
) {
    val field = schemaState?.fields?.findByPath(dotted = path)
    if (schemaState == null || field == null) {
        InspectorPlaceholder(modifier = modifier)
        return
    }
    FieldInspector(
        dottedPath = path,
        field = field,
        editable = !schemaState.isReadOnly,
        usages = catalogUsages,
        onSelectMember = { member -> onSelectItem(InspectorItem.Field(id = member)) },
        onFieldChange = { updated ->
            onSchemaChange(
                schemaState.copy(fields = schemaState.fields.updateAtPath(dotted = path) { updated }),
            )
        },
        modifier = modifier,
    )
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ActionBranch(
    name: String,
    actionState: ActionEditorState?,
    catalogUsages: Int?,
    onActionChange: (ActionEditorState) -> Unit,
    modifier: Modifier,
) {
    val action = actionState?.actions?.firstOrNull { candidate -> candidate.name == name }
    if (actionState == null || action == null) {
        InspectorPlaceholder(modifier = modifier)
        return
    }
    ActionInspector(
        action = action,
        editable = !actionState.isReadOnly,
        usages = catalogUsages,
        onActionChange = { updated ->
            onActionChange(
                actionState.copy(
                    actions = actionState.actions.map { candidate ->
                        if (candidate.name == name) updated else candidate
                    },
                ),
            )
        },
        modifier = modifier,
    )
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

/** Shown when nothing is selected, or when a selection no longer resolves. */
@Composable
internal fun InspectorPlaceholder(modifier: Modifier = Modifier) {
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
