package ui.builder.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary
import ui.builder.components.row.ActionRowEditor
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.mutable.BuilderEditorState

// The `then` half of the Builder: the action rows.

@Composable
internal fun ThenSection(
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
