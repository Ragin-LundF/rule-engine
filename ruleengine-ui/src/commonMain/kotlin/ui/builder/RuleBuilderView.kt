package ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BorderColor
import ui.BgSurface
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.components.ActionRowEditor
import ui.builder.components.ConditionRowEditor
import ui.builder.components.RuleBuilderHeader
import ui.components.TinyButton

/**
 * Editable visual representation of a single rule in Builder mode.
 *
 * Renders WHEN / THEN blocks derived from [editorState].
 * On every change, calls [onDslChange] with freshly generated DSL text so the
 * Code editor stays in sync. Falls back to a friendly message for locked rules.
 *
 * [allRuleIds] is the full list of rule IDs available for selection.
 * [onRuleSelected] is called when the user picks a different rule from the dropdown.
 * [onAddRule] is called when the user clicks "+ Add rule".
 */
@Composable
fun RuleBuilderView(
    editorState: BuilderEditorState,
    allRuleIds: List<String> = emptyList(),
    onRuleSelected: (String) -> Unit = {},
    onAddRule: () -> Unit = {},
    onRenameRule: (oldId: String, newId: String) -> Unit = { _, _ -> },
    catalogFields: List<CatalogFieldInfo> = emptyList(),
    catalogActions: List<CatalogActionInfo> = emptyList(),
    onConditionSelected: (String) -> Unit = {},
    onDslChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RuleBuilderHeader(
            ruleIds = allRuleIds,
            selectedRuleId = editorState.ruleId,
            onRuleSelected = onRuleSelected,
            onAddRule = onAddRule,
            onRenameRule = onRenameRule,
        )

        if (editorState.isLocked) {
            LockedBuilderMessage(reason = editorState.lockReason)
            return@Column
        }

        BuilderCard {
            WhenSection(
                editorState = editorState,
                catalogFields = catalogFields,
                onConditionSelected = onConditionSelected,
                onDslChange = onDslChange,
            )
        }

        BuilderCard {
            ThenSection(
                editorState = editorState,
                catalogActions = catalogActions,
                onDslChange = onDslChange,
            )
        }
    }
}

@Composable
private fun BuilderCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun WhenSection(
    editorState: BuilderEditorState,
    catalogFields: List<CatalogFieldInfo>,
    onConditionSelected: (String) -> Unit,
    onDslChange: (String) -> Unit,
) {
    SectionHeader(
        title = "WHEN",
        subtitle = "Conditions are evaluated top to bottom",
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    renderNodes(
        nodes = editorState.conditionNodes,
        catalogFields = catalogFields,
        onConditionSelected = onConditionSelected,
        onDslChange = onDslChange,
        editorState = editorState,
        isFirstLevel = true,
    )

    if (editorState.conditionNodes.isEmpty()) {
        Text(
            text = "(no conditions)",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }

    Spacer(modifier = Modifier.height(height = 8.dp))
    AddButton(
        label = "+ Condition",
        onClick = {
            val defaultField = catalogFields.firstOrNull()?.id ?: ""
            val defaultOperator = catalogFields.firstOrNull()?.let { field ->
                OperatorOptions.forField(
                    fieldType = field.type,
                    schemaOperators = field.operators,
                ).firstOrNull()
            } ?: "equals"
            editorState.addCondition(
                defaultField = defaultField,
                defaultOperator = defaultOperator,
            )
            emitDslChange(editorState = editorState, onDslChange = onDslChange)
        },
    )
}

/**
 * Recursively renders [MutableConditionNode] entries with join selectors
 * and condition editors / group containers between them.
 */
@Composable
private fun renderNodes(
    nodes: List<MutableConditionNode>,
    catalogFields: List<CatalogFieldInfo>,
    onConditionSelected: (String) -> Unit,
    onDslChange: (String) -> Unit,
    editorState: BuilderEditorState,
    isFirstLevel: Boolean,
) {
    nodes.forEachIndexed { index, node ->
        if (index > 0 && isFirstLevel) {
            JoinSelectorRow(
                join = node.joinToPrevious,
                onJoinSelected = { selectedJoin ->
                    node.joinToPrevious = selectedJoin
                    emitDslChange(editorState = editorState, onDslChange = onDslChange)
                },
            )
        }

        when (node) {
            is MutableConditionNode.Leaf -> {
                ConditionRowEditor(
                    condition = node.inner,
                    fields = catalogFields,
                    onSelected = { onConditionSelected(node.inner.id) },
                    onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
                    onRemove = {
                        editorState.removeCondition(id = node.id)
                        emitDslChange(editorState = editorState, onDslChange = onDslChange)
                    },
                )
            }
            is MutableConditionNode.Group -> {
                GroupContainer(
                    group = node,
                    catalogFields = catalogFields,
                    onConditionSelected = onConditionSelected,
                    onDslChange = onDslChange,
                    editorState = editorState,
                )
            }
        }

        // Non-first-level nodes have join selectors rendered inside group containers
        if (index > 0 && !isFirstLevel) {
            JoinSelectorRow(
                join = node.joinToPrevious,
                onJoinSelected = { selectedJoin ->
                    node.joinToPrevious = selectedJoin
                    emitDslChange(editorState = editorState, onDslChange = onDslChange)
                },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/**
 * A visually distinct container for a parenthesized group of conditions.
 * Renders child nodes with an indication that they are grouped.
 */
@Composable
private fun GroupContainer(
    group: MutableConditionNode.Group,
    catalogFields: List<CatalogFieldInfo>,
    onConditionSelected: (String) -> Unit,
    onDslChange: (String) -> Unit,
    editorState: BuilderEditorState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 6.dp),
            )
            .padding(8.dp),
    ) {
        // Group bracket label
        Text(
            text = "(   )",
            style = MaterialTheme.typography.caption,
            color = PrimaryBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        renderNodes(
            nodes = group.nodes.toList(),
            catalogFields = catalogFields,
            onConditionSelected = onConditionSelected,
            onDslChange = onDslChange,
            editorState = editorState,
            isFirstLevel = false,
        )

        if (group.nodes.isEmpty()) {
            Text(
                text = "(empty group)",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ThenSection(
    editorState: BuilderEditorState,
    catalogActions: List<CatalogActionInfo>,
    onDslChange: (String) -> Unit,
) {
    SectionHeader(
        title = "THEN",
        subtitle = null,
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    if (editorState.actions.isEmpty()) {
        Text(
            text = "(no actions)",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    } else {
        editorState.actions.forEach { action ->
            ActionRowEditor(
                action = action,
                actions = catalogActions,
                onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
                onRemove = {
                    editorState.removeAction(id = action.id)
                    emitDslChange(editorState = editorState, onDslChange = onDslChange)
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(height = 8.dp))
    AddButton(
        label = "+ Action",
        onClick = {
            val defaultAction = catalogActions.firstOrNull()
            val defaultName = defaultAction?.name ?: ""
            val defaultArgCount = if (defaultAction?.argType == "none") 0 else 1
            editorState.addAction(
                defaultName = defaultName,
                defaultArgCount = defaultArgCount,
            )
            emitDslChange(editorState = editorState, onDslChange = onDslChange)
        },
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
        )
        subtitle?.let {
            Text(
                text = "  $it",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun JoinSelectorRow(
    join: String,
    onJoinSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TinyButton(
            text = "AND",
            primary = join != "or",
            onClick = { onJoinSelected("and") },
        )
        TinyButton(
            text = "OR",
            primary = join == "or",
            onClick = { onJoinSelected("or") },
        )
    }
}

@Composable
private fun AddButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = PrimaryBlue,
            style = MaterialTheme.typography.button,
        )
    }
}

@Composable
private fun LockedBuilderMessage(reason: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (reason == "No rule selected.") "Select a rule from the manifest to edit it here."
            else "⚠  Advanced syntax detected",
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface,
        )
        if (reason != "No rule selected.") {
            Text(text = reason, style = MaterialTheme.typography.body2, color = TextSecondary)
            Text(
                text = "Switch to Code mode to edit this rule.",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        }
    }
}

private fun emitDslChange(
    editorState: BuilderEditorState,
    onDslChange: (String) -> Unit,
) {
    val dsl = BuilderToRuleDsl.generate(state = editorState)
    if (dsl != null) {
        onDslChange(dsl)
    }
}