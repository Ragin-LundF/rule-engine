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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
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

/**
 * Editable visual representation of a single rule in Builder mode.
 *
 * Renders WHEN / AND / THEN blocks derived from [editorState].
 * On every change, calls [onDslChange] with freshly generated DSL text so the
 * Code editor stays in sync. Falls back to a friendly message for locked rules.
 *
 * @param editorState  mutable builder state; created via [BuilderEditorState.fromBuilderRule].
 * @param catalogFields field catalog used to populate field dropdowns (id → type/operators).
 * @param catalogActions action catalog used to populate action dropdowns.
 * @param onDslChange  called with new DSL text whenever the user edits a condition or action.
 */
@Composable
fun RuleBuilderView(
    editorState: BuilderEditorState,
    catalogFields: List<CatalogFieldInfo> = emptyList(),
    catalogActions: List<CatalogActionInfo> = emptyList(),
    onDslChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (editorState.isLocked) {
        LockedBuilderMessage(reason = editorState.lockReason, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RuleBuilderHeader(ruleId = editorState.ruleId)

        BuilderCard {
            WhenSection(
                editorState = editorState,
                catalogFields = catalogFields,
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
    onDslChange: (String) -> Unit,
) {
    SectionHeader(
        title = "WHEN",
        subtitle = when (editorState.conditionJoin) {
            ConditionJoin.AND, ConditionJoin.SINGLE -> "All conditions are met"
            ConditionJoin.OR -> "Any condition is met"
        },
        actionLabel = "+ Condition",
        onAction = {
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

    Spacer(modifier = Modifier.height(height = 8.dp))

    editorState.conditions.forEachIndexed { index, condition ->
        if (index > 0) {
            JoinLabel(text = "AND")
        }
        ConditionRowEditor(
            condition = condition,
            fields = catalogFields,
            onChanged = { emitDslChange(editorState = editorState, onDslChange = onDslChange) },
            onRemove = {
                editorState.removeCondition(id = condition.id)
                emitDslChange(editorState = editorState, onDslChange = onDslChange)
            },
        )
    }

    if (editorState.conditions.isEmpty()) {
        Text(
            text = "(no conditions)",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
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
        actionLabel = "+ Action",
        onAction = {
            val defaultName = catalogActions.firstOrNull()?.name ?: ""
            editorState.addAction(defaultName = defaultName)
            emitDslChange(editorState = editorState, onDslChange = onDslChange)
        },
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
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        OutlinedButton(onClick = onAction) {
            Text(text = actionLabel, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun JoinLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        modifier = Modifier.padding(vertical = 2.dp),
    )
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
