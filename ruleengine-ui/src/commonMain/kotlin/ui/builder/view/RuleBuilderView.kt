package ui.builder.view

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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.BorderColor
import ui.PrimaryBlue
import ui.TextSecondary
import ui.builder.BuilderToRuleDsl
import ui.builder.OperatorOptions
import ui.builder.components.RuleBuilderHeader
import ui.builder.components.row.PlainTextField
import ui.builder.model.BuilderLockKind
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState

/**
 * Editable visual representation of a single rule in Builder mode.
 *
 * Renders WHEN / THEN blocks derived from [editorState].
 * On every change, calls [onDslChange] with freshly generated DSL text so the
 * Code editor stays in sync. Falls back to a friendly message for locked rules.
 *
 * [allRuleIds] is the full list of rule IDs available for selection.
 * [onRuleSelected] is called when the user picks a different rule from the dropdown.
 * [onAddRule] is called when the user clicks "+ Add rule".
 */
@Composable
fun RuleBuilderView(
    editorState: BuilderEditorState,
    allRuleIds: List<String> = emptyList(),
    onRuleSelected: (String) -> Unit = {},
    onAddRule: () -> Unit = {},
    onRenameRule: (oldId: String, newId: String) -> Unit = { _, _ -> },
    catalogFields: List<CatalogFieldInfo> = emptyList(),
    catalogActions: List<CatalogActionInfo> = emptyList(),
    onConditionSelected: (String) -> Unit = {},
    onDslChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RuleBuilderHeader(
            ruleIds = allRuleIds,
            selectedRuleId = editorState.ruleId,
            onRuleSelected = onRuleSelected,
            onAddRule = onAddRule,
            onRenameRule = onRenameRule,
        )

        if (editorState.isLocked) {
            LockedBuilderMessage(kind = editorState.lockKind, reason = editorState.lockReason)
            return@Column
        }

        BuilderCard {
            DescriptionSection(editorState = editorState, onDslChange = onDslChange)
        }

        BuilderCard {
            WhenSection(
                editorState = editorState,
                catalogFields = catalogFields,
                onConditionSelected = onConditionSelected,
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

/**
 * The rule's optional `description` clause.
 *
 * Sits above WHEN because that is where it belongs in the generated DSL, and because a rule author
 * arriving at a rule should read what it is for before reading how it decides.
 */
@Composable
private fun DescriptionSection(
    editorState: BuilderEditorState,
    onDslChange: (String) -> Unit,
) {
    SectionHeader(
        title = "DESCRIPTION",
        subtitle = "Optional — one sentence, shown in the exported rule overview",
    )

    Spacer(modifier = Modifier.height(height = 8.dp))

    PlainTextField(
        value = editorState.description,
        placeholder = "What is this rule for?",
        onValueChange = { text ->
            editorState.description = text
            emitDslChange(editorState = editorState, onDslChange = onDslChange)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
}

@Composable
internal fun AddButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = PrimaryBlue,
            style = MaterialTheme.typography.button,
        )
    }
}

@Composable
private fun LockedBuilderMessage(
    kind: BuilderLockKind,
    reason: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (kind) {
                BuilderLockKind.NO_RULE_SELECTED -> "Select a rule from the manifest to edit it here."
                else -> "⚠  This rule can only be edited in Code mode"
            },
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colors.onSurface,
        )
        if (kind != BuilderLockKind.NO_RULE_SELECTED) {
            Text(text = reason, style = MaterialTheme.typography.body2, color = TextSecondary)
            Text(
                text = "Switch to Code mode to edit this rule.",
                style = MaterialTheme.typography.body2,
                color = TextSecondary,
            )
        }
    }
}

/**
 * Operator a freshly added condition starts on: the first one [field] actually allows, so a new row is
 * usable without touching the dropdown. Falls back to `equals`, which every scalar type supports.
 */
internal fun defaultOperatorFor(field: CatalogFieldInfo?): String {
    val allowed = field?.let {
        OperatorOptions.forField(fieldType = it.type, schemaOperators = it.operators)
    }
    return allowed?.firstOrNull() ?: OperatorOptions.EQUALS
}

internal fun emitDslChange(
    editorState: BuilderEditorState,
    onDslChange: (String) -> Unit,
) {
    val dsl = BuilderToRuleDsl.generate(state = editorState)
    if (dsl != null) {
        onDslChange(dsl)
    }
}
