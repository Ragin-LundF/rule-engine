package ui.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.components.ComparisonRowEditor
import ui.builder.components.ConditionRowEditor
import ui.components.TinyButton
import kotlin.random.Random

// The `when` half of the Builder: the condition tree, its grouping controls and the
// join/negate toggles. Split from RuleBuilderView, which owns the card and the sections
// around it.

@Composable
internal fun WhenSection(
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
    WhenAddButtons(
        editorState = editorState,
        catalogFields = catalogFields,
        onDslChange = onDslChange,
        onAdded = { selectedGroupIds.clear() },
    )
}

/**
 * The three ways to add to a `when` block: a plain condition, a calculation, or a group.
 *
 * Each clears the multi-select first — the ids it holds refer to positions that the new node has
 * just moved, so grouping against a stale selection would nest the wrong rows.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun WhenAddButtons(
    editorState: BuilderEditorState,
    catalogFields: List<CatalogFieldInfo>,
    onDslChange: (String) -> Unit,
    onAdded: () -> Unit,
) {
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
                onAdded()
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
                onAdded()
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
                onAdded()
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
    }}

/**
 * Recursively renders [MutableConditionNode] entries with join selectors
 * and condition editors / group containers between them.
 *
 * At the first level, leaf nodes show a checkbox for multi-select grouping.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun renderNodes(
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
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
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
internal fun GroupContainer(
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
        GroupHeader(
            onUngroup = {
                editorState.ungroup(id = group.id)
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
            onRemove = {
                editorState.removeCondition(id = group.id)
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )

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

/** A group's own controls: the bracket label it is named by, and Ungroup / Remove. */
@Suppress("FunctionNaming")
@Composable
private fun GroupHeader(onUngroup: () -> Unit, onRemove: () -> Unit) {
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
            TinyButton(text = "Ungroup", onClick = onUngroup)
            TinyButton(text = "×", onClick = onRemove)
        }
    }
}

/**
 * A floating bar that appears when 2+ conditions are selected for grouping.
 */
@Composable
internal fun GroupSelectionBar(
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
internal fun ConditionCheckbox(
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
internal fun JoinSelectorRow(
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

/** Toggle for `not`, shown on every node so a condition can be negated in place. */
@Composable
internal fun NotToggle(
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
