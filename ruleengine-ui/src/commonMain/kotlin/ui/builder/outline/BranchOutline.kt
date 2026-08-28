package ui.builder.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.ast.AssignmentKindAst
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentPurple
import ui.AccentRed
import ui.TextSecondary
import ui.builder.OperandText
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderAction
import ui.builder.model.mutable.MutableBuilderVariable
import ui.components.TinyButton

/**
 * One outcome block as an outline section: its rows, then what can be added to it.
 *
 * The three blocks read in the order the DSL requires — `then`, `else`, `not_exists` — so a rule edited
 * here always parses back. An optional block appears only once it holds something, because "the block
 * exists" and "the block has something in it" are the same state in the text: an empty `else` does not
 * parse.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun BranchOutline(
    state: BuilderEditorState,
    branch: RuleBranch,
    catalogActions: List<CatalogActionInfo>,
    selectedStatementId: String?,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val actions = state.actionsOf(branch = branch)
    val variables = state.variablesOf(branch = branch)
    val stops = state.stopOf(branch = branch)

    Column(modifier = Modifier.fillMaxWidth()) {
        BranchHeader(branch = branch)

        StatementRows(
            state = state,
            branch = branch,
            variables = variables,
            actions = actions,
            selectedStatementId = selectedStatementId,
            onSelectStatement = onSelectStatement,
            onEdited = onEdited,
            onMessage = onMessage,
        )

        if (actions.isEmpty() && variables.isEmpty() && !stops) {
            Text(
                text = "(no outcome yet)",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
                modifier = Modifier.padding(start = 22.dp, bottom = 2.dp),
            )
        }

        // `stop` is a badge and never a row: there is nothing about it to edit, so a row with a
        // dropdown and a value box would offer choices that do not exist. It is held as a flag on the
        // branch, which is what keeps it pinned to the end however the author edits around it.
        if (stops) {
            StopBadge(
                onRemove = {
                    state.setStop(branch = branch, stop = false)
                    onEdited()
                },
            )
        }

        BranchAddRow(
            state = state,
            branch = branch,
            catalogActions = catalogActions,
            stops = stops,
            onEdited = onEdited,
        )
    }
}

/**
 * The block's statements, assignments first.
 *
 * That order is the engine's, not a presentation choice: an assignment publishes its value before the
 * same rule's actions resolve, so an action that reads `$total` sees what the `set` above it wrote. A
 * reader who sees them in the other order would reasonably conclude the opposite.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun StatementRows(
    state: BuilderEditorState,
    branch: RuleBranch,
    variables: List<MutableBuilderVariable>,
    actions: List<MutableBuilderAction>,
    selectedStatementId: String?,
    onSelectStatement: (RuleBranch, String) -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    variables.forEach { variable ->
        StatementRow(
            text = assignmentText(assignment = variable),
            selected = variable.id == selectedStatementId,
            onSelect = { onSelectStatement(branch, variable.id) },
            onRemove = {
                removeStatement(
                    state = state,
                    id = variable.id,
                    remove = { state.removeVariable(id = variable.id) },
                    onEdited = onEdited,
                    onMessage = onMessage,
                )
            },
        )
    }

    actions.forEach { action ->
        StatementRow(
            text = actionText(action = action),
            selected = action.id == selectedStatementId,
            onSelect = { onSelectStatement(branch, action.id) },
            onRemove = {
                removeStatement(
                    state = state,
                    id = action.id,
                    remove = { state.removeAction(id = action.id) },
                    onEdited = onEdited,
                    onMessage = onMessage,
                )
            },
        )
    }
}

/**
 * Removes a statement, or explains why it cannot go.
 *
 * The guard lives in [BuilderEditorState] rather than here because it is about what the DSL accepts —
 * a `then` block with nothing in it does not parse — so every caller has to respect it, not just this
 * canvas. All this function does is choose between the refusal and the removal.
 */
private fun removeStatement(
    state: BuilderEditorState,
    id: String,
    remove: () -> Unit,
    onEdited: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val blocked = state.blockedRemoval(id = id)
    if (blocked != null) {
        onMessage(blocked)
    } else {
        remove()
        onEdited()
    }
}

/** The block's name and what it means, which is the part a reader needs and an author forgets. */
@Suppress("FunctionNaming")
@Composable
private fun BranchHeader(branch: RuleBranch) {
    val (title, colour, hint) = when (branch) {
        RuleBranch.THEN -> Triple("THEN", AccentGreen, "output when the condition holds")
        RuleBranch.ELSE -> Triple("ELSE", AccentPurple, "output when the condition is false")
        RuleBranch.NOT_EXISTS -> Triple(
            "NOT EXISTS",
            AccentOrange,
            "output when the record could not decide it",
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = colour,
        )
        Text(text = hint, style = MaterialTheme.typography.caption, color = TextSecondary)
    }
}

/** One statement, one line, with its remove appearing only while it is selected. */
@Suppress("FunctionNaming")
@Composable
private fun StatementRow(
    text: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "→",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
            modifier = Modifier.width(width = 16.dp),
        )
        Column(modifier = Modifier.weight(weight = 1f)) {
            StatementText(text = text, selected = selected, onClick = onSelect)
        }
        if (selected) {
            TinyButton(text = "×", onClick = onRemove)
        }
    }
}

/** The `stop` marker: present or absent, nothing in between. */
@Suppress("FunctionNaming")
@Composable
private fun StopBadge(onRemove: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 22.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "stop",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = AccentRed,
            modifier = Modifier
                .clip(shape = RoundedCornerShape(size = 4.dp))
                .background(color = AccentRed.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
            text = "no rule after this one is evaluated",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        TinyButton(text = "×", onClick = onRemove)
    }
}

/** What can be added to this block. `+ stop` disappears once the block has one. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun BranchAddRow(
    state: BuilderEditorState,
    branch: RuleBranch,
    catalogActions: List<CatalogActionInfo>,
    stops: Boolean,
    onEdited: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        TinyButton(
            text = "+ action",
            onClick = {
                val first = catalogActions.firstOrNull()
                state.addAction(
                    defaultName = first?.name.orEmpty(),
                    defaultArgCount = if (first?.argType == "none") 0 else 1,
                    branch = branch,
                )
                onEdited()
            },
        )
        TinyButton(
            text = "+ set",
            onClick = {
                state.addVariable(
                    defaultName = nextVariableName(state = state),
                    branch = branch,
                    kind = AssignmentKindAst.SET,
                )
                onEdited()
            },
        )
        TinyButton(
            text = "+ add to list",
            onClick = {
                state.addVariable(
                    defaultName = nextVariableName(state = state),
                    branch = branch,
                    kind = AssignmentKindAst.ADD,
                )
                onEdited()
            },
        )
        if (!stops) {
            TinyButton(
                text = "+ stop",
                onClick = {
                    state.setStop(branch = branch, stop = true)
                    onEdited()
                },
            )
        }
    }
}

/**
 * The branches this rule does not have, as one offer rather than two ghost sections.
 *
 * Each used to get a full-width row with its own explanatory header, which made the empty half of a rule
 * taller on the page than the half carrying its logic — and put the least important thing on screen
 * first in line to be clipped. What a branch *means* is worth saying, but the place to say it is the
 * branch's own header once it exists, which is where [BranchHeader] already says it.
 */
@Suppress("FunctionNaming")
@Composable
internal fun AddBranchRows(
    branches: List<RuleBranch>,
    catalogActions: List<CatalogActionInfo>,
    state: BuilderEditorState,
    onEdited: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = "Also handle",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        branches.forEach { branch ->
            TinyButton(
                text = if (branch == RuleBranch.ELSE) "+ else" else "+ not_exists",
                onClick = {
                    val first = catalogActions.firstOrNull()
                    state.addAction(
                        defaultName = first?.name.orEmpty(),
                        defaultArgCount = if (first?.argType == "none") 0 else 1,
                        branch = branch,
                    )
                    onEdited()
                },
            )
        }
    }
}

/**
 * A placeholder name that does not collide with one this rule already assigns.
 *
 * A blank name would generate `set  = …`, which does not parse — so the rule file would break the
 * moment the row is added rather than once the author has finished filling it in.
 */
private fun nextVariableName(state: BuilderEditorState): String {
    val existing = state.variables + state.elseVariables + state.notExistsVariables
    val taken = existing.map { variable -> variable.name }.toSet()
    var index = existing.size + 1
    while ("value$index" in taken) {
        index++
    }
    return "value$index"
}

private fun actionText(action: MutableBuilderAction): String {
    val extraction = action.extraction
    val prefix = if (extraction == null) {
        ""
    } else {
        "extract ${extraction.sourceField} regex(\"${extraction.pattern}\", ${extraction.groupIndex}) "
    }
    val argument = action.arguments.firstOrNull()?.takeIf { it.isNotBlank() }
    return prefix + action.name + (argument?.let { " $it" } ?: "")
}

private fun assignmentText(assignment: MutableBuilderVariable): String {
    val value = OperandText.toDsl(operand = assignment.expression)
    val name = assignment.name.ifBlank { "…" }
    return when (assignment.kind) {
        AssignmentKindAst.SET -> "set $name = $value"
        AssignmentKindAst.ADD -> "add $value to $name"
    }
}
