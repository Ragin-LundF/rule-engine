package ui.builder.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.ast.AssignmentKindAst
import ui.AccentOrange
import ui.TextSecondary
import ui.builder.components.row.ActionRowEditor
import ui.builder.components.row.VariableRowEditor
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.components.StatusBadge
import ui.components.TinyButton

// The output half of the Builder: the `set` rows, then the action rows. Rendered once per branch —
// THEN when the condition held, ELSE when it did not, NOT_EXISTS when the record carried no data to
// decide it — because every block holds the same kinds of row and they differ only in when the engine
// reaches them.

@Composable
internal fun BranchSection(
    editorState: BuilderEditorState,
    branch: RuleBranch,
    catalogActions: List<CatalogActionInfo>,
    catalogFields: List<CatalogFieldInfo>,
    onDslChange: (String) -> Unit,
) {
    val variables = editorState.variablesOf(branch = branch)
    val actions = editorState.actionsOf(branch = branch)

    SectionHeader(
        title = branchTitle(branch = branch),
        subtitle = branchSubtitle(branch = branch),
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    // Rendered above the actions because that is the order the engine applies them in: a `set`
    // publishes its value before the same rule's actions resolve.
    variables.forEach { variable ->
        VariableRowEditor(
            variable = variable,
            fields = catalogFields,
            onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
            onRemove = {
                editorState.removeVariable(id = variable.id)
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        Spacer(modifier = Modifier.height(height = 4.dp))
    }

    if (actions.isEmpty()) {
        // A branch that only publishes variables — or only stops the run — is complete, so it must not
        // read as unfinished.
        if (variables.isEmpty() && !editorState.stopOf(branch = branch)) {
            Text(
                text = "(no actions)",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        }
    } else {
        actions.forEach { action ->
            ActionRowEditor(
                action = action,
                actions = catalogActions,
                fields = catalogFields,
                onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
                onRemove = {
                    editorState.removeAction(id = action.id)
                    emitDslChange(editorState = editorState, onDslChange = onDslChange)
                },
            )
        }
    }

    // Always rendered after the rows, never between them: `stop` ends the branch, and the state holds it
    // as a flag rather than a row so adding more output cannot push anything below it.
    if (editorState.stopOf(branch = branch)) {
        Spacer(modifier = Modifier.height(height = 8.dp))
        StopBadge(
            onRemove = {
                editorState.setStop(branch = branch, stop = false)
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
    }

    Spacer(modifier = Modifier.height(height = 8.dp))
    BranchAddButtons(
        editorState = editorState,
        branch = branch,
        catalogActions = catalogActions,
        onDslChange = onDslChange,
    )
}

/**
 * The `stop` marker: a badge with a remove control, not an editable row.
 *
 * There is nothing to edit about a `stop` — it is present or it is not — so a row with a dropdown and a
 * value box would offer choices that do not exist.
 */
@Suppress("FunctionNaming")
@Composable
private fun StopBadge(onRemove: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(label = "stop", color = AccentOrange)
        Text(
            text = "no rule after this one is evaluated",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
        TinyButton(text = "×", onClick = onRemove)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun BranchAddButtons(
    editorState: BuilderEditorState,
    branch: RuleBranch,
    catalogActions: List<CatalogActionInfo>,
    onDslChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        AddButton(
            label = "+ Action",
            onClick = {
                val defaultAction = catalogActions.firstOrNull()
                val defaultName = defaultAction?.name ?: ""
                val defaultArgCount = if (defaultAction?.argType == "none") 0 else 1
                editorState.addAction(
                    defaultName = defaultName,
                    defaultArgCount = defaultArgCount,
                    branch = branch,
                )
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        AddButton(
            label = "+ Variable",
            onClick = {
                editorState.addVariable(
                    defaultName = nextVariableName(editorState = editorState),
                    branch = branch,
                    kind = AssignmentKindAst.SET,
                )
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        AddButton(
            label = "+ Add to list",
            onClick = {
                editorState.addVariable(
                    defaultName = nextVariableName(editorState = editorState),
                    branch = branch,
                    kind = AssignmentKindAst.ADD,
                )
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        // Hidden once the branch has one, so there is never a second badge to remove.
        if (!editorState.stopOf(branch = branch)) {
            AddButton(
                label = "+ Stop",
                onClick = {
                    editorState.setStop(branch = branch, stop = true)
                    emitDslChange(editorState = editorState, onDslChange = onDslChange)
                },
            )
        }
    }
}

/**
 * A placeholder name that does not collide with one this rule already assigns.
 *
 * A blank name would generate `set  = …`, which does not parse, so the rule file would break the
 * moment the row is added rather than once the author has finished filling it in. Every branch is
 * considered: a name is unique per rule, not per branch.
 */
private fun nextVariableName(editorState: BuilderEditorState): String {
    val existing = editorState.variables + editorState.elseVariables + editorState.notExistsVariables
    val taken = existing.map { it.name }.toSet()
    var index = existing.size + 1
    while ("value$index" in taken) {
        index++
    }
    return "value$index"
}

private fun branchTitle(branch: RuleBranch): String {
    return when (branch) {
        RuleBranch.THEN -> "THEN"
        RuleBranch.ELSE -> "ELSE"
        RuleBranch.NOT_EXISTS -> "NOT EXISTS"
    }
}

private fun branchSubtitle(branch: RuleBranch): String? {
    return when (branch) {
        RuleBranch.THEN -> null
        RuleBranch.ELSE -> "Optional — output when the condition does not hold"
        RuleBranch.NOT_EXISTS -> "Optional — output when the record carries no data to decide the condition"
    }
}
