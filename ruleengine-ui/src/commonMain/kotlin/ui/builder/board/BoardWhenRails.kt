package ui.builder.board

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
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.BuilderToRuleDsl
import ui.builder.RowIssues
import ui.builder.board.model.DropTarget
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.components.TinyButton

/**
 * The rule's `when` block on the board: precedence as geometry, and grouping as a drag.
 *
 * The outline canvas already shows the same structure as bracket rails. What the board adds is the
 * gesture: a row is dragged onto another row to group the two, or into an existing group to join it.
 * That is the one thing about grouping that reading cannot teach — where the brackets *could* go — and
 * it is why the board carries drag while the outline carries the keyboard path.
 *
 * A refused drop says why rather than springing back silently, which is the only way a refusal teaches
 * the DSL instead of looking like a bug.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun BoardWhenRails(
    nodes: List<MutableConditionNode>,
    state: BuilderEditorState,
    drag: BoardDragState,
    depth: Int,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 3.dp),
    ) {
        nodes.forEachIndexed { index, node ->
            if (index > 0) {
                JoinToggle(
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
                    drag = drag,
                    depth = depth,
                    selectedNodeId = selectedNodeId,
                    onSelectNode = onSelectNode,
                    onEdited = onEdited,
                    onMessage = onMessage,
                )

                else -> DraggableRow(
                    node = node,
                    state = state,
                    drag = drag,
                    selected = node.id == selectedNodeId,
                    onSelect = { onSelectNode(node.id) },
                    onEdited = onEdited,
                    onMessage = onMessage,
                )
            }
        }
    }
}

/**
 * One condition, as a card that can be picked up and dropped on.
 *
 * It is both: a row is a drag source *and* a drop target, because dropping row B on row A is how a
 * group of two is created from nothing. Registering itself as a target while it is the thing being
 * dragged is harmless — [validateDrop] refuses a row dropped on itself.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun DraggableRow(
    node: MutableConditionNode,
    state: BuilderEditorState,
    drag: BoardDragState,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val subject = BoardDragState.DragSubject.Row(nodeId = node.id)
    val isDragged = drag.dragged == subject
    val isHovered = drag.hovered == DropTarget.Row(nodeId = node.id)
    val issue = RowIssues.of(node = node)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(
                color = when {
                    isHovered && drag.refusal == null -> PrimaryGlow
                    selected -> PrimaryGlow
                    else -> BgSurface
                },
            )
            .border(
                width = 1.dp,
                color = when {
                    isHovered && drag.refusal != null -> AccentRed
                    isHovered -> AccentCyan
                    selected -> PrimaryBlue
                    else -> BorderColor
                },
                shape = RoundedCornerShape(size = 6.dp),
            )
            .dropTarget(state = drag, target = DropTarget.Row(nodeId = node.id))
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        RowGrip(node = node, state = state, drag = drag, subject = subject, onEdited = onEdited, onMessage = onMessage)
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text(
                text = rowText(node = node),
                style = MaterialTheme.typography.body2,
                color = if (node.negated) AccentRed else TextPrimary,
            )
            issue?.let { text ->
                Text(text = "⚠ $text", style = MaterialTheme.typography.caption, color = AccentOrange)
            }
        }
        if (selected) {
            TinyButton(
                text = "( )",
                onClick = {
                    state.wrapInGroup(id = node.id)
                    onEdited()
                },
            )
        }
    }
}

/**
 * The drag handle.
 *
 * A handle rather than the whole card, because the card is clickable to select: a card that both selects
 * on click and drags on press makes every selection feel like a drag that failed.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun RowGrip(
    node: MutableConditionNode,
    state: BuilderEditorState,
    drag: BoardDragState,
    subject: BoardDragState.DragSubject,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    Text(
        text = "⠿",
        style = MaterialTheme.typography.caption,
        color = if (drag.dragged == subject) PrimaryBlue else TextSecondary,
        modifier = Modifier.draggable(
            state = drag,
            subject = subject,
            validate = { dragged, target ->
                validateDrop(state = state, subject = dragged, target = target)
            },
            onDrop = { target ->
                applyRowDrop(state = state, nodeId = node.id, target = target)
                onEdited()
            },
            onRefused = onMessage,
        ),
    )
}

/** A group, as a coloured rail with the bulk joins on it. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun GroupRail(
    group: MutableConditionNode.Group,
    state: BuilderEditorState,
    drag: BoardDragState,
    depth: Int,
    selectedNodeId: String?,
    onSelectNode: (String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val colour = railColour(depth = depth)
    val isHovered = drag.hovered == DropTarget.Group(groupId = group.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(intrinsicSize = IntrinsicSize.Min)
            .dropTarget(state = drag, target = DropTarget.Group(groupId = group.id)),
    ) {
        Box(
            modifier = Modifier
                .width(width = 3.dp)
                .fillMaxHeight()
                .clip(shape = RoundedCornerShape(size = 2.dp))
                .background(color = if (isHovered) AccentCyan else colour.copy(alpha = 0.6f)),
        )
        Column(
            modifier = Modifier
                .weight(weight = 1f)
                .background(color = colour.copy(alpha = if (isHovered) 0.12f else 0.05f))
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        ) {
            GroupHeader(
                group = group,
                state = state,
                colour = colour,
                selected = group.id == selectedNodeId,
                onSelect = { onSelectNode(group.id) },
                onEdited = onEdited,
            )
            BoardWhenRails(
                nodes = group.nodes.toList(),
                state = state,
                drag = drag,
                depth = depth + 1,
                selectedNodeId = selectedNodeId,
                onSelectNode = onSelectNode,
                onEdited = onEdited,
                onMessage = onMessage,
            )
            if (group.nodes.isEmpty()) {
                Text(
                    text = "drop a row here — an empty group renders as () and does not parse",
                    style = MaterialTheme.typography.caption,
                    color = TextSecondary,
                )
            }
        }
    }
}

/** The rail's label and, once selected, the joins that apply to all of its children at once. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun GroupHeader(
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
            TinyButton(text = "all AND", onClick = { setAllJoins(group = group, join = "and", onEdited = onEdited) })
            TinyButton(text = "all OR", onClick = { setAllJoins(group = group, join = "or", onEdited = onEdited) })
            TinyButton(
                text = "ungroup",
                onClick = {
                    state.ungroup(id = group.id)
                    onEdited()
                },
            )
        }
    }
}

private fun setAllJoins(group: MutableConditionNode.Group, join: String, onEdited: () -> Unit) {
    group.nodes.forEachIndexed { index, child ->
        if (index > 0) {
            child.joinToPrevious = join
        }
    }
    onEdited()
}

/** `AND` / `OR` between two rows, as a state you press rather than a row of two buttons. */
@Suppress("FunctionNaming")
@Composable
private fun JoinToggle(join: String, onToggle: () -> Unit) {
    val isOr = join == "or"
    Text(
        text = if (isOr) "OR" else "AND",
        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
        color = if (isOr) AccentPurple else TextSecondary,
        modifier = Modifier
            .padding(start = 10.dp)
            .clip(shape = RoundedCornerShape(percent = 50))
            .background(color = if (isOr) AccentPurple.copy(alpha = 0.14f) else BgSurface)
            .border(
                width = 1.dp,
                color = if (isOr) AccentPurple.copy(alpha = 0.35f) else BorderColor,
                shape = RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

/** Depth read as colour, so nesting is seen rather than counted. */
private fun railColour(depth: Int): Color = when (depth % RAIL_COLOURS) {
    0 -> PrimaryBlue
    1 -> AccentCyan
    else -> AccentPurple
}

/** How many colours the rails cycle through before repeating. */
private const val RAIL_COLOURS: Int = 3

/** What the row says, taken from the generator so the card cannot drift from the file. */
private fun rowText(node: MutableConditionNode): String {
    val body = BuilderToRuleDsl.renderRow(node = node) ?: "( … )"
    return if (node.negated) "not $body" else body
}
