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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.components.ActionRowEditor
import ui.builder.components.ComparisonRowEditor
import ui.builder.components.ConditionRowEditor
import ui.builder.components.PlainTextField
import ui.builder.components.RuleBuilderHeader
import ui.components.TinyButton
import kotlin.random.Random

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
            LockedBuilderMessage(kind = editorState.lockKind, reason = editorState.lockReason)
            return@Column
        }

        BuilderCard {
            DescriptionSection(editorState = editorState, onDslChange = onDslChange)
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

/**
 * The rule's optional `description` clause.
 *
 * Sits above WHEN because that is where it belongs in the generated DSL, and because a rule author
 * arriving at a rule should read what it is for before reading how it decides.
 */
@Composable
private fun DescriptionSection(
    editorState: BuilderEditorState,
    onDslChange: (String) -> Unit,
) {
    SectionHeader(
        title = "DESCRIPTION",
        subtitle = "Optional — one sentence, shown in the exported rule overview",
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    PlainTextField(
        value = editorState.description,
        placeholder = "What is this rule for?",
        onValueChange = { text ->
            editorState.description = text
            emitDslChange(editorState = editorState, onDslChange = onDslChange)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WhenSection(
    editorState: BuilderEditorState,
    catalogFields: List<CatalogFieldInfo>,
    onConditionSelected: (String) -> Unit,
    onDslChange: (String) -> Unit,
) {
    val selectedGroupIds = remember { mutableStateListOf<String>() }

    SectionHeader(
        title = "WHEN",
        subtitle = "Conditions are evaluated top to bottom",
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    // Group selection bar (appears when 2+ top-level conditions are selected)
    if (selectedGroupIds.size >= 2) {
        GroupSelectionBar(
            selectedCount = selectedGroupIds.size,
            onGroup = {
                editorState.groupConditions(ids = selectedGroupIds.toSet())
                selectedGroupIds.clear()
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
            onClearSelection = { selectedGroupIds.clear() },
        )
        Spacer(modifier = Modifier.height(height = 4.dp))
    }

    renderNodes(
        nodes = editorState.conditionNodes,
        catalogFields = catalogFields,
        onConditionSelected = onConditionSelected,
        onDslChange = onDslChange,
        editorState = editorState,
        isFirstLevel = true,
        selectedGroupIds = selectedGroupIds,
    )

    if (editorState.conditionNodes.isEmpty()) {
        Text(
            text = "(no conditions)",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }

    Spacer(modifier = Modifier.height(height = 8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AddButton(
            label = "+ Condition",
            onClick = {
                val start = catalogFields.scalarPaths().firstOrNull()
                editorState.addCondition(
                    defaultField = start?.id ?: "",
                    defaultOperator = defaultOperatorFor(field = start),
                )
                selectedGroupIds.clear()
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        AddButton(
            label = "+ Calculation",
            onClick = {
                editorState.addComparison(
                    left = OperandRules.defaultOperand(
                        kind = OperandRules.OperandKind.AGGREGATE,
                        fields = catalogFields,
                        previous = BuilderOperand.Literal(text = "", numeric = false),
                    ),
                    operator = OperatorOptions.COMPARISON_NUMERIC.first(),
                    right = BuilderOperand.Literal(text = "0", numeric = true),
                )
                selectedGroupIds.clear()
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        AddButton(
            label = "+ Group",
            onClick = {
                val joinToPrevious = editorState.conditionNodes.lastOrNull()?.let { "and" } ?: ""
                val group = MutableConditionNode.Group(
                    id = "grp-${Random.nextInt(until = 4)}",
                    nodes = mutableStateListOf(),
                    joinToPrevious = joinToPrevious,
                )
                editorState.conditionNodes.add(group)
                selectedGroupIds.clear()
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
    }
}

/**
 * Recursively renders [MutableConditionNode] entries with join selectors
 * and condition editors / group containers between them.
 *
 * At the first level, leaf nodes show a checkbox for multi-select grouping.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
private fun renderNodes(
    nodes: List<MutableConditionNode>,
    catalogFields: List<CatalogFieldInfo>,
    onConditionSelected: (String) -> Unit,
    onDslChange: (String) -> Unit,
    editorState: BuilderEditorState,
    isFirstLevel: Boolean,
    selectedGroupIds: MutableList<String>? = null,
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Show checkbox only for first-level leaf nodes (for grouping)
            if (isFirstLevel && node is MutableConditionNode.Leaf && selectedGroupIds != null) {
                ConditionCheckbox(
                    checked = node.id in selectedGroupIds,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (node.id !in selectedGroupIds) selectedGroupIds.add(node.id)
                        } else {
                            selectedGroupIds.remove(node.id)
                        }
                    },
                )
            }

            NotToggle(
                negated = node.negated,
                onToggle = { negated ->
                    node.negated = negated
                    emitDslChange(editorState = editorState, onDslChange = onDslChange)
                },
            )

            when (node) {
                is MutableConditionNode.Leaf -> {
                    ConditionRowEditor(
                        condition = node.inner,
                        fields = catalogFields,
                        onSelected = { onConditionSelected(node.inner.id) },
                        onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
                        onRemove = {
                            editorState.removeCondition(id = node.id)
                            selectedGroupIds?.remove(node.id)
                            emitDslChange(editorState = editorState, onDslChange = onDslChange)
                        },
                        onSwitchToAdvanced = {
                            editorState.toComparison(
                                id = node.id,
                                operator = OperatorOptions.COMPARISON_NUMERIC.first(),
                            )
                            emitDslChange(editorState = editorState, onDslChange = onDslChange)
                        },
                    )
                }
                is MutableConditionNode.ComparisonLeaf -> {
                    ComparisonRowEditor(
                        comparison = node.inner,
                        fields = catalogFields,
                        onSelected = { onConditionSelected(node.inner.id) },
                        onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
                        onRemove = {
                            editorState.removeCondition(id = node.id)
                            selectedGroupIds?.remove(node.id)
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
 * Renders child nodes with controls for ungrouping, removing, and adding
 * conditions inside the group.
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
                color = PrimaryBlue.copy(alpha = 0.4f),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .padding(8.dp),
    ) {
        // Header: bracket label + Ungroup + Remove buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "(   )",
                style = MaterialTheme.typography.caption,
                color = PrimaryBlue,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TinyButton(
                    text = "Ungroup",
                    onClick = {
                        editorState.ungroup(id = group.id)
                        emitDslChange(editorState = editorState, onDslChange = onDslChange)
                    },
                )
                TinyButton(
                    text = "×",
                    onClick = {
                        editorState.removeCondition(id = group.id)
                        emitDslChange(editorState = editorState, onDslChange = onDslChange)
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(height = 4.dp))

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

        Spacer(modifier = Modifier.height(height = 4.dp))

        // Add condition inside the group
        AddButton(
            label = "+ Condition",
            onClick = {
                val start = catalogFields.scalarPaths().firstOrNull()
                editorState.addConditionInside(
                    groupId = group.id,
                    defaultField = start?.id ?: "",
                    defaultOperator = defaultOperatorFor(field = start),
                )
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
    }
}

/**
 * A floating bar that appears when 2+ conditions are selected for grouping.
 */
@Composable
private fun GroupSelectionBar(
    selectedCount: Int,
    onGroup: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = PrimaryBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(size = 6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$selectedCount conditions selected",
            style = MaterialTheme.typography.caption,
            color = PrimaryBlue,
        )
        TinyButton(
            text = "Group",
            primary = true,
            onClick = onGroup,
        )
        TinyButton(
            text = "Clear",
            onClick = onClearSelection,
        )
    }
}

/**
 * A small checkbox used for selecting top-level conditions before grouping.
 */
@Composable
private fun ConditionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.size(size = 20.dp),
        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
    )
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
private fun LockedBuilderMessage(
    kind: BuilderLockKind,
    reason: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (kind) {
                BuilderLockKind.NO_RULE_SELECTED -> "Select a rule from the manifest to edit it here."
                else -> "⚠  This rule can only be edited in Code mode"
            },
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface,
        )
        if (kind != BuilderLockKind.NO_RULE_SELECTED) {
            Text(text = reason, style = MaterialTheme.typography.body2, color = TextSecondary)
            Text(
                text = "Switch to Code mode to edit this rule.",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        }
    }
}

/** Toggle for `not`, shown on every node so a condition can be negated in place. */
@Composable
private fun NotToggle(
    negated: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TinyButton(
        text = "NOT",
        primary = negated,
        onClick = { onToggle(!negated) },
        modifier = modifier,
    )
}

/**
 * Operator a freshly added condition starts on: the first one [field] actually allows, so a new row is
 * usable without touching the dropdown. Falls back to `equals`, which every scalar type supports.
 */
private fun defaultOperatorFor(field: CatalogFieldInfo?): String {
    val allowed = field?.let {
        OperatorOptions.forField(fieldType = it.type, schemaOperators = it.operators)
    }
    return allowed?.firstOrNull() ?: OperatorOptions.EQUALS
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
