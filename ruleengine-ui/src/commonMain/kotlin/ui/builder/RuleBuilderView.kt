package ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.TextSecondary

/**
 * Editable visual representation of a single rule in Builder mode.
 *
 * Renders WHEN / AND / THEN blocks derived from [editorState].
 * On every change, calls [onDslChange] with freshly generated DSL text so the
 * Code editor stays in sync. Falls back to a friendly message for locked rules.
 *
 * @param editorState  mutable builder state; created via [BuilderEditorState.fromBuilderRule].
 * @param catalogFields field catalog used to populate field dropdowns (id → type/operators).
 * @param onDslChange  called with new DSL text whenever the user edits a condition or action.
 */
@Composable
fun RuleBuilderView(
    editorState: BuilderEditorState,
    catalogFields: List<CatalogFieldInfo> = emptyList(),
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val whenLabel = when (editorState.conditionJoin) {
            ConditionJoin.AND, ConditionJoin.SINGLE -> "WHEN  All conditions are met"
            ConditionJoin.OR -> "WHEN  Any condition is met"
        }
        Text(
            text = whenLabel,
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
        )

        editorState.conditions.forEachIndexed { index, condition ->
            if (index > 0) {
                Text(
                    text = "AND",
                    style = MaterialTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            EditableConditionRow(
                condition = condition,
                catalogFields = catalogFields,
                onChanged = {
                    val dsl = BuilderToRuleDsl.generate(editorState)
                    if (dsl != null) onDslChange(dsl)
                },
            )
        }

        Text(
            text = "THEN",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (editorState.actions.isEmpty()) {
            Text(
                text = "(no actions)",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        } else {
            editorState.actions.forEach { action ->
                EditableActionRow(
                    action = action,
                    onChanged = {
                        val dsl = BuilderToRuleDsl.generate(editorState)
                        if (dsl != null) onDslChange(dsl)
                    },
                )
            }
        }
    }
}

// ── Editable condition row ────────────────────────────────────────────────────

@Composable
private fun EditableConditionRow(
    condition: MutableBuilderCondition,
    catalogFields: List<CatalogFieldInfo>,
    onChanged: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Field dropdown
        val fieldInfo = catalogFields.firstOrNull { it.id == condition.field }
        DropdownChip(
            label = condition.field,
            options = catalogFields.map { it.id },
            onSelect = { selected ->
                condition.field = selected
                // Reset operator to first valid option for new field type
                val newFieldInfo = catalogFields.firstOrNull { it.id == selected }
                val ops = OperatorOptions.forField(
                    fieldType = newFieldInfo?.type ?: "text",
                    schemaOperators = newFieldInfo?.operators ?: emptyList(),
                )
                if (condition.operator !in ops) condition.operator = ops.firstOrNull() ?: condition.operator
                onChanged()
            },
        )

        // Operator dropdown
        val operators = OperatorOptions.forField(
            fieldType = fieldInfo?.type ?: "text",
            schemaOperators = fieldInfo?.operators ?: emptyList(),
        )
        DropdownChip(
            label = condition.operator,
            options = operators,
            onSelect = { selected ->
                condition.operator = selected
                onChanged()
            },
        )

        // Value editor
        ConditionValueEditor(
            condition = condition,
            modifier = Modifier,
        )
    }
}

// ── Editable action row ───────────────────────────────────────────────────────

@Composable
private fun EditableActionRow(
    action: MutableBuilderAction,
    onChanged: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = action.name,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface,
        )
        action.arguments.forEachIndexed { idx, arg ->
            OutlinedTextField(
                value = arg,
                onValueChange = { newVal ->
                    action.arguments[idx] = newVal
                    onChanged()
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Shared dropdown chip ──────────────────────────────────────────────────────

@Composable
private fun DropdownChip(
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }) {
        Text(text = label, style = MaterialTheme.typography.body2)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(option)
                }) {
                    Text(option)
                }
            }
        }
    }
}

// ── Locked state message ──────────────────────────────────────────────────────

@Composable
private fun LockedBuilderMessage(reason: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (reason == "No rule selected.") "Select a rule from the left panel to preview it here."
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

/** Lightweight field info passed from the platform layer to avoid JVM-only types in commonMain. */
data class CatalogFieldInfo(
    val id: String,
    val type: String,
    val operators: List<String> = emptyList(),
)
