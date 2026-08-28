package ui.builder.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.AccentCyan
import ui.AccentOrange
import ui.AccentPurple
import ui.BgHover
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextSecondary
import ui.builder.RowIssues
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.selection.SelectionStep
import ui.components.TinyButton

/**
 * The `when` block as an outline: one line per row, joins on the gutter rail, groups as bracket rails.
 *
 * Three things changed from the card stack this replaces.
 *
 * A row is one line and carries no toolbar — its controls appear on the right only for the selected
 * row, so the expression is what the eye lands on.
 *
 * `AND` / `OR` is a pill in the row's own gutter rather than a full-width row of two buttons between
 * every pair of conditions. A five-condition rule used to spend half its height on joins, and a join
 * that looks like a button reads as an action rather than as the state it is.
 *
 * A group is a bracket rail: precedence is geometry, so it can be read without parsing keywords.
 * Grouping is shift-click plus one press, not tick-two-checkboxes-then-find-the-bar.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun OutlineRows(
    nodes: List<MutableConditionNode>,
    state: BuilderEditorState,
    depth: Int,
    selectedNodeId: String?,
    selectedSteps: List<SelectionStep>?,
    picked: List<String>,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onTogglePick: (String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    nodes.forEachIndexed { index, node ->
        if (index > 0) {
            JoinPill(
                join = node.joinToPrevious,
                onToggle = {
                    node.joinToPrevious = if (node.joinToPrevious == "or") "and" else "or"
                    onEdited()
                },
            )
        }
        when (node) {
            is MutableConditionNode.Group -> GroupRail(
                group = node,
                state = state,
                depth = depth,
                selectedNodeId = selectedNodeId,
                selectedSteps = selectedSteps,
                picked = picked,
                onSelectNode = onSelectNode,
                onTogglePick = onTogglePick,
                onEdited = onEdited,
                onMessage = onMessage,
            )

            else -> ConditionOutlineRow(
                node = node,
                state = state,
                index = index,
                depth = depth,
                selected = node.id == selectedNodeId,
                selectedSteps = if (node.id == selectedNodeId) selectedSteps else null,
                pickedForGrouping = node.id in picked,
                onSelect = { steps -> onSelectNode(node.id, steps) },
                onTogglePick = { onTogglePick(node.id) },
                onEdited = onEdited,
                onMessage = onMessage,
            )
        }
    }
}

/** One row: its number, its tokens, and — only when selected — what can be done to it. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ConditionOutlineRow(
    node: MutableConditionNode,
    state: BuilderEditorState,
    index: Int,
    depth: Int,
    selected: Boolean,
    selectedSteps: List<SelectionStep>?,
    pickedForGrouping: Boolean,
    onSelect: (List<SelectionStep>) -> Unit,
    onTogglePick: () -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (selected) PrimaryGlow else Color.Transparent)
            .border(
                width = 1.dp,
                color = when {
                    pickedForGrouping -> PrimaryBlue
                    selected -> PrimaryBlue.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(size = 6.dp),
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = if (depth > 0) "·" else "${index + 1}",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.width(width = 16.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(weight = 1f)) {
            when (node) {
                is MutableConditionNode.Leaf -> ConditionTokens(
                    condition = node.inner,
                    selectedSteps = selectedSteps,
                    onSelect = onSelect,
                )

                is MutableConditionNode.ComparisonLeaf -> ComparisonTokens(
                    comparison = node.inner,
                    selectedSteps = selectedSteps,
                    onSelect = onSelect,
                )

                is MutableConditionNode.Group -> Unit
            }
            RowIssues.of(node = node)?.let { issue ->
                IssueNote(text = issue)
            }
        }
        if (selected) {
            RowActions(
                node = node,
                state = state,
                onTogglePick = onTogglePick,
                onEdited = onEdited,
                onMessage = onMessage,
            )
        }
    }
}

/**
 * What this row still needs, under the row.
 *
 * A note rather than a wavy underline: the underline says *that* something is wrong and makes the
 * author hunt for what, which on a one-line row with four parts is a guess. There is room to say it.
 */
@Suppress("FunctionNaming")
@Composable
private fun IssueNote(text: String) {
    Text(
        text = "⚠ $text",
        style = MaterialTheme.typography.caption,
        color = AccentOrange,
        modifier = Modifier.padding(start = 3.dp, top = 1.dp),
    )
}

/** The selected row's own controls, which is the only time they take any space. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RowActions(
    node: MutableConditionNode,
    state: BuilderEditorState,
    onTogglePick: () -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 3.dp)) {
        TinyButton(
            text = "¬",
            onClick = {
                node.negated = !node.negated
                onEdited()
            },
        )
        TinyButton(
            text = "( )",
            onClick = {
                state.wrapInGroup(id = node.id)
                onEdited()
            },
        )
        TinyButton(text = "⊕", onClick = onTogglePick)
        TinyButton(
            text = "×",
            onClick = {
                val blocked = state.blockedRemoval(id = node.id)
                if (blocked != null) {
                    onMessage(blocked)
                } else {
                    state.removeCondition(id = node.id)
                    onEdited()
                }
            },
        )
    }
}

/**
 * A group, drawn as a bracket rail with its own controls on it.
 *
 * The colour changes with depth so nesting is readable at a glance rather than counted.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun GroupRail(
    group: MutableConditionNode.Group,
    state: BuilderEditorState,
    depth: Int,
    selectedNodeId: String?,
    selectedSteps: List<SelectionStep>?,
    picked: List<String>,
    onSelectNode: (String, List<SelectionStep>) -> Unit,
    onTogglePick: (String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val railColour = when (depth % 3) {
        0 -> PrimaryBlue
        1 -> AccentCyan
        else -> AccentPurple
    }
    // IntrinsicSize.Min is what gives the rail a height to fill: without it the Box has no intrinsic
    // height of its own and collapses to nothing, leaving the group with no bracket.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(intrinsicSize = IntrinsicSize.Min)
            .padding(vertical = 2.dp),
    ) {
        // The bracket, as an actual edge rather than a border on a padded box: a group is read as the
        // span its rail covers, so the rail has to be exactly as tall as its contents.
        Box(
            modifier = Modifier
                .width(width = 2.dp)
                .fillMaxHeight()
                .clip(shape = RoundedCornerShape(size = 1.dp))
                .background(color = railColour.copy(alpha = 0.55f)),
        )
        Column(
            modifier = Modifier
                .weight(weight = 1f)
                .background(color = railColour.copy(alpha = 0.05f))
                .padding(start = 8.dp, top = 3.dp, bottom = 3.dp),
        ) {
            GroupRailHeader(
                group = group,
                state = state,
                colour = railColour,
                selected = group.id == selectedNodeId,
                onSelect = { onSelectNode(group.id, emptyList()) },
                onEdited = onEdited,
            )
            OutlineRows(
                nodes = group.nodes.toList(),
                state = state,
                depth = depth + 1,
                selectedNodeId = selectedNodeId,
                selectedSteps = selectedSteps,
                picked = picked,
                onSelectNode = onSelectNode,
                onTogglePick = onTogglePick,
                onEdited = onEdited,
                onMessage = onMessage,
            )
            if (group.nodes.isEmpty()) {
                Text(
                    text = "(empty group — it renders as () and does not parse)",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                )
            }
        }
    }
}

/** The rail's own label and controls: how it joins, and the two bulk joins plus ungroup. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun GroupRailHeader(
    group: MutableConditionNode.Group,
    state: BuilderEditorState,
    colour: Color,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdited: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = if (group.negated) "not ( ${group.nodes.size} )" else "( ${group.nodes.size} )",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colour,
        )
        if (selected) {
            TinyButton(
                text = "all AND",
                onClick = {
                    group.nodes.forEachIndexed { index, child ->
                        if (index > 0) child.joinToPrevious = "and"
                    }
                    onEdited()
                },
            )
            TinyButton(
                text = "all OR",
                onClick = {
                    group.nodes.forEachIndexed { index, child ->
                        if (index > 0) child.joinToPrevious = "or"
                    }
                    onEdited()
                },
            )
            TinyButton(
                text = "ungroup",
                onClick = {
                    state.ungroup(id = group.id)
                    onEdited()
                },
            )
            TinyButton(
                text = "+ row",
                onClick = {
                    state.addConditionInside(groupId = group.id)
                    onEdited()
                },
            )
        }
    }
}

/**
 * The join between two rows, as a state on the gutter rather than a row of its own.
 *
 * `OR` is coloured because it is the one that changes how the rule reads; `AND` is the default and
 * stays quiet.
 */
@Suppress("FunctionNaming")
@Composable
private fun JoinPill(join: String, onToggle: () -> Unit) {
    val isOr = join == "or"
    Row(
        modifier = Modifier.padding(start = 22.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isOr) "OR" else "AND",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = if (isOr) AccentPurple else TextSecondary,
            modifier = Modifier
                .clip(shape = RoundedCornerShape(percent = 50))
                .background(color = if (isOr) AccentPurple.copy(alpha = 0.14f) else BgHover)
                .border(
                    width = 1.dp,
                    color = if (isOr) AccentPurple.copy(alpha = 0.35f) else BorderColor,
                    shape = RoundedCornerShape(percent = 50),
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}
