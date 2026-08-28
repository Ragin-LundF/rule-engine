package ui.builder.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.ast.AssignmentKindAst
import ui.AccentCyan
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentPurple
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.OperandText
import ui.builder.board.model.DropTarget
import ui.builder.model.mutable.BuilderEditorState
import ui.components.TinyButton

/**
 * The three outcomes side by side, each stating when it runs.
 *
 * The outline shows them stacked in DSL order, which is right for reading the file. Side by side is
 * right for a different question: *what does this rule do in each case* — which the stack cannot answer,
 * because comparing `then` with `not_exists` means scrolling past `else`.
 *
 * All three lanes are always drawn, including the ones the rule does not have. An absent `else` is a
 * decision — the rule does nothing when the condition is false — and a lane that says so is how that
 * decision becomes visible instead of being invisible by omission.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun BoardLanes(
    state: BuilderEditorState,
    drag: BoardDragState,
    selectedStatementId: String?,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RuleBranch.entries.forEach { branch ->
            Lane(
                branch = branch,
                state = state,
                drag = drag,
                selectedStatementId = selectedStatementId,
                onSelectStatement = onSelectStatement,
                onEdited = onEdited,
                onMessage = onMessage,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

/** One lane: its name, when it runs, its cards, and a `stop` at the foot if it has one. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun Lane(
    branch: RuleBranch,
    state: BuilderEditorState,
    drag: BoardDragState,
    selectedStatementId: String?,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = DropTarget.Lane(branch = branch)
    val isHovered = drag.hovered == target
    val colour = laneColour(branch = branch)
    val actions = state.actionsOf(branch = branch)
    val variables = state.variablesOf(branch = branch)
    val stops = state.stopOf(branch = branch)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = colour.copy(alpha = if (isHovered) 0.10f else 0.04f))
            .border(
                width = 1.dp,
                color = when {
                    isHovered && drag.refusal != null -> AccentRed
                    isHovered -> AccentCyan
                    else -> BorderColor
                },
                shape = RoundedCornerShape(size = 8.dp),
            )
            .dropTarget(state = drag, target = target)
            .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        LaneHeader(branch = branch, colour = colour)

        LaneCards(
            branch = branch,
            state = state,
            drag = drag,
            selectedStatementId = selectedStatementId,
            onSelectStatement = onSelectStatement,
            onEdited = onEdited,
            onMessage = onMessage,
        )

        if (actions.isEmpty() && variables.isEmpty() && !stops) {
            Text(
                text = emptyLaneText(branch = branch),
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }

        // `stop` is a badge at the foot of the lane, never a card: it is a flag on the branch, there is
        // nothing about it to edit, and it always applies after everything else in the lane.
        if (stops) {
            StopBadge(
                onRemove = {
                    state.setStop(branch = branch, stop = false)
                    onEdited()
                },
            )
        }
    }
}

/**
 * A lane's cards: assignments first, then actions.
 *
 * The engine's order, not a layout choice — an assignment publishes its value before the same rule's
 * actions resolve, so an action reading `$total` sees what the `set` above it wrote.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun LaneCards(
    branch: RuleBranch,
    state: BuilderEditorState,
    drag: BoardDragState,
    selectedStatementId: String?,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    state.variablesOf(branch = branch).forEach { variable ->
        StatementCard(
            text = assignmentText(
                name = variable.name,
                kind = variable.kind,
                value = OperandText.toDsl(operand = variable.expression),
            ),
            statementId = variable.id,
            branch = branch,
            state = state,
            drag = drag,
            selected = variable.id == selectedStatementId,
            onSelect = { onSelectStatement(branch, variable.id) },
            onEdited = onEdited,
            onMessage = onMessage,
        )
    }

    state.actionsOf(branch = branch).forEach { action ->
        StatementCard(
            text = actionText(name = action.name, argument = action.arguments.firstOrNull()),
            statementId = action.id,
            branch = branch,
            state = state,
            drag = drag,
            selected = action.id == selectedStatementId,
            onSelect = { onSelectStatement(branch, action.id) },
            onEdited = onEdited,
            onMessage = onMessage,
        )
    }
}

/** The lane's name and the condition under which it runs. */
@Suppress("FunctionNaming")
@Composable
private fun LaneHeader(branch: RuleBranch, colour: Color) {
    Column {
        Text(
            text = laneTitle(branch = branch),
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = colour,
        )
        Text(
            text = laneWhen(branch = branch),
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}

/** One outcome, draggable between lanes by its grip. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun StatementCard(
    text: String,
    statementId: String,
    branch: RuleBranch,
    state: BuilderEditorState,
    drag: BoardDragState,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val subject = BoardDragState.DragSubject.Statement(
        statementId = statementId,
        from = DropTarget.Lane(branch = branch),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = if (selected) PrimaryGlow else BgSurface)
            .border(
                width = 1.dp,
                color = if (selected) PrimaryBlue else BorderColor,
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
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
                    applyStatementDrop(
                        state = state,
                        statementId = statementId,
                        from = branch,
                        target = target,
                    )
                    onEdited()
                },
                onRefused = onMessage,
            ),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.body2,
            color = TextPrimary,
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

/** The `stop` marker. */
@Suppress("FunctionNaming")
@Composable
private fun StopBadge(onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 5.dp),
    ) {
        Text(
            text = "⊘ stop",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = AccentRed,
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 4.dp))
                .background(color = AccentRed.copy(alpha = 0.14f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        TinyButton(text = "×", onClick = onRemove)
    }
}

private fun laneTitle(branch: RuleBranch): String = when (branch) {
    RuleBranch.THEN -> "THEN"
    RuleBranch.ELSE -> "ELSE"
    RuleBranch.NOT_EXISTS -> "NOT EXISTS"
}

private fun laneColour(branch: RuleBranch): Color = when (branch) {
    RuleBranch.THEN -> AccentGreen
    RuleBranch.ELSE -> AccentPurple
    RuleBranch.NOT_EXISTS -> AccentOrange
}

private fun laneWhen(branch: RuleBranch): String = when (branch) {
    RuleBranch.THEN -> "the condition held"
    RuleBranch.ELSE -> "the condition was false"
    RuleBranch.NOT_EXISTS -> "the record could not decide it"
}

/**
 * What an empty lane says.
 *
 * Each names the consequence rather than saying "empty", because that is the thing worth knowing: a
 * missing `not_exists` is the difference between a record with no data being treated as a `false` and
 * being treated as its own case.
 */
private fun emptyLaneText(branch: RuleBranch): String = when (branch) {
    RuleBranch.THEN -> "nothing yet — a rule with no `then` does not parse"
    RuleBranch.ELSE -> "nothing — the rule does nothing when the condition is false"
    RuleBranch.NOT_EXISTS -> "nothing — missing data falls through to `else`"
}

private fun actionText(name: String, argument: String?): String {
    val arg = argument?.takeIf { text -> text.isNotBlank() }
    return name.ifBlank { "…" } + (arg?.let { text -> " $text" } ?: "")
}

private fun assignmentText(name: String, kind: AssignmentKindAst, value: String): String {
    val shown = name.ifBlank { "…" }
    return when (kind) {
        AssignmentKindAst.SET -> "set $shown = $value"
        AssignmentKindAst.ADD -> "add $value to $shown"
    }
}
