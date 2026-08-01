package ui.builder.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.components.row.ActionRowEditor
import ui.builder.components.row.VariableRowEditor
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState

// The `then` half of the Builder: the `set` rows, then the action rows.

@Composable
internal fun ThenSection(
    editorState: BuilderEditorState,
    catalogActions: List<CatalogActionInfo>,
    catalogFields: List<CatalogFieldInfo>,
    onDslChange: (String) -> Unit,
) {
    SectionHeader(
        title = "THEN",
        subtitle = null,
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    // Rendered above the actions because that is the order the engine applies them in: a `set`
    // publishes its value before the same rule's actions resolve.
    editorState.variables.forEach { variable ->
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

    if (editorState.actions.isEmpty()) {
        // A rule that only publishes variables is complete, so it must not read as unfinished.
        if (editorState.variables.isEmpty()) {
            Text(
                text = "(no actions)",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        }
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
    ThenAddButtons(
        editorState = editorState,
        catalogActions = catalogActions,
        onDslChange = onDslChange,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun ThenAddButtons(
    editorState: BuilderEditorState,
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
                )
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
        AddButton(
            label = "+ Variable",
            onClick = {
                editorState.addVariable(defaultName = nextVariableName(editorState = editorState))
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
    }
}

/**
 * A placeholder name that does not collide with one this rule already assigns.
 *
 * A blank name would generate `set  = …`, which does not parse, so the rule file would break the
 * moment the row is added rather than once the author has finished filling it in.
 */
private fun nextVariableName(editorState: BuilderEditorState): String {
    val taken = editorState.variables.map { it.name }.toSet()
    var index = editorState.variables.size + 1
    while ("value$index" in taken) {
        index++
    }
    return "value$index"
}
