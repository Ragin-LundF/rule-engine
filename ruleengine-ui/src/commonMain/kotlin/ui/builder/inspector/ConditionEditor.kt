package ui.builder.inspector

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.builder.OperandRules
import ui.builder.OperandText
import ui.builder.OperatorOptions
import ui.builder.RowForm
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.editor.TypedValueEditor
import ui.builder.model.BuilderOperand
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.catalog.fieldAtPath
import ui.builder.model.catalog.scalarPaths
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableBuilderComparison
import ui.builder.model.mutable.MutableBuilderCondition
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.selection.SelectionStep
import ui.components.TinyButton

/**
 * The editor for one condition row — simple or computed — with the side pickers that decide which.
 *
 * There is no "make it a comparison" button here, and no way back either, because neither is a thing
 * the author does: they pick what a *side* is, and the row's DSL form follows. `RowForm` states that
 * rule; this renders it.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ConditionEditor(
    condition: MutableBuilderCondition,
    state: BuilderEditorState,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scalarFields = fields.scalarPaths()
    // Exact match first, then a resolution. The two differ for a spelling `scalarPaths` does not
    // enumerate but the engine accepts — an alias standing in for the last segment of a dotted path,
    // `reports.income.SPENDING_TO_INCOME_RATIO`. Falling through to "text" there gave the row the
    // wrong operator list and marked a declared field as one the schema does not have.
    val fieldInfo = scalarFields.firstOrNull { it.id == condition.field }
        ?: fields.fieldAtPath(segments = condition.field.split(PATH_SEPARATOR))
    val fieldType = fieldInfo?.type ?: "text"
    val blocked = RowForm.blockedPromotion(condition = condition)

    Column(modifier = modifier.fillMaxWidth()) {
        DslEcho(text = conditionEcho(condition = condition))

        ConditionLeftSide(
            condition = condition,
            state = state,
            fields = fields,
            scalarFields = scalarFields,
            blocked = blocked,
            onEdited = onEdited,
            onSelect = onSelect,
            onMessage = onMessage,
        )

        OperatorField(
            condition = condition,
            fieldInfo = fieldInfo,
            fieldType = fieldType,
            onEdited = onEdited,
        )

        ConditionRightSide(
            condition = condition,
            state = state,
            fields = fields,
            fieldInfo = fieldInfo,
            fieldType = fieldType,
            blocked = blocked,
            onEdited = onEdited,
            onSelect = onSelect,
            onMessage = onMessage,
        )

        ConditionModifiers(condition = condition, fieldType = fieldType, onEdited = onEdited)
    }
}

/**
 * The paths offered for a row, with the author's own spelling kept when it is one the enumeration does
 * not produce but the engine resolves. Without it a legal path is shown as an off-list value.
 */
private fun fieldOptions(scalarFields: List<CatalogFieldInfo>, selected: String): List<String> {
    val ids = scalarFields.map { field -> field.id }
    return if (selected.isBlank() || selected in ids) ids else ids + selected
}

/** How a dotted path is spelled, both in the DSL and in a flat schema key. */
private const val PATH_SEPARATOR: Char = '.'

/** The left side of a simple condition: the kind picker, then the field it names. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ConditionLeftSide(
    condition: MutableBuilderCondition,
    state: BuilderEditorState,
    fields: BuilderCatalog,
    scalarFields: List<CatalogFieldInfo>,
    blocked: String?,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
    onMessage: (String) -> Unit,
) {
    InspectorSection(title = "Left side")
    OperandKindPicker(
        current = OperandRules.OperandKind.FIELD,
        disabledReason = blocked,
        onSelect = { kind ->
            promoteSide(
                condition = condition,
                state = state,
                fields = fields,
                kind = kind,
                side = SelectionStep.Left,
                onEdited = onEdited,
                onSelect = onSelect,
                onMessage = onMessage,
            )
        },
    )
    InspectorField(label = "Field", hint = "${scalarFields.size} comparable paths") {
        DropdownSelector(
            selected = condition.field,
            options = fieldOptions(scalarFields = scalarFields, selected = condition.field),
            onSelected = { selected ->
                condition.field = selected
                val chosen = scalarFields.firstOrNull { it.id == selected }
                val allowed = OperatorOptions.forCatalogField(
                    fieldId = selected,
                    fieldType = chosen?.type ?: "text",
                    schemaOperators = chosen?.operators ?: emptyList(),
                )
                if (condition.operator !in allowed) {
                    condition.operator = allowed.firstOrNull() ?: condition.operator
                }
                onEdited()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The right side of a simple condition: the kind picker, then the typed value. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ConditionRightSide(
    condition: MutableBuilderCondition,
    state: BuilderEditorState,
    fields: BuilderCatalog,
    fieldInfo: CatalogFieldInfo?,
    fieldType: String,
    blocked: String?,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
    onMessage: (String) -> Unit,
) {
    InspectorSection(title = "Right side")
    OperandKindPicker(
        current = OperandRules.OperandKind.VALUE,
        disabledReason = blocked,
        onSelect = { kind ->
            promoteSide(
                condition = condition,
                state = state,
                fields = fields,
                kind = kind,
                side = SelectionStep.Right,
                onEdited = onEdited,
                onSelect = onSelect,
                onMessage = onMessage,
            )
        },
    )
    InspectorField(label = "Value", hint = fieldInfo?.format?.ifBlank { null }) {
        TypedValueEditor(
            condition = condition,
            onChanged = onEdited,
            fieldType = fieldType,
            valueHint = fieldInfo?.format.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The operator list, always including the one the row actually has. */
@Suppress("FunctionNaming")
@Composable
private fun OperatorField(
    condition: MutableBuilderCondition,
    fieldInfo: CatalogFieldInfo?,
    fieldType: String,
    onEdited: () -> Unit,
) {
    val allowed = OperatorOptions.forCatalogField(
        fieldId = condition.field,
        fieldType = fieldType,
        schemaOperators = fieldInfo?.operators ?: emptyList(),
    )
    // A row can legitimately carry a symbolic operator the type's named list does not include — it
    // arrived from a comparison. Dropping it from the list would show nothing as selected.
    val shown = if (condition.operator in allowed || condition.operator.isBlank()) {
        allowed
    } else {
        allowed + condition.operator
    }
    InspectorField(label = "Operator", hint = "what a $fieldType allows") {
        InspectorOptions(
            options = shown,
            selected = condition.operator,
            onSelect = { selected ->
                condition.operator = selected
                onEdited()
            },
        )
    }
    if (fieldType == "text") {
        InspectorNote(text = "Ordering operators are absent: the engine rejects > on text.")
    }
}

/** `not` and `ignoreCase`, the latter disabled with its reason where folding case changes nothing. */
@Suppress("FunctionNaming")
@Composable
private fun ConditionModifiers(
    condition: MutableBuilderCondition,
    fieldType: String,
    onEdited: () -> Unit,
) {
    val textish = OperatorOptions.isTextType(fieldType = fieldType) || fieldType == "string_set"
    InspectorSection(title = "Modifiers")
    InspectorToggle(
        label = "not",
        checked = condition.negated,
        onCheckedChange = { value ->
            condition.negated = value
            onEdited()
        },
        hint = "negate this condition",
    )
    InspectorToggle(
        label = "ignoreCase",
        checked = condition.ignoreCase,
        enabled = textish,
        onCheckedChange = { value ->
            condition.ignoreCase = value
            onEdited()
        },
        hint = if (textish) {
            "compares without regard to case"
        } else {
            "only meaningful on text — folding case leaves a $fieldType untouched"
        },
    )
}

/**
 * The editor for a comparison row: two operand cards around a symbolic operator.
 *
 * The row goes back to a simple condition on its own once both sides are plain again — `RowForm`
 * re-derives after every operand edit — so there is nothing to press for that either.
 */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
internal fun ComparisonEditor(
    comparison: MutableBuilderComparison,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DslEcho(text = comparisonEcho(comparison = comparison))

        ComparisonSide(
            title = "Left side",
            operand = comparison.left,
            fields = fields,
            onOperandChanged = { value ->
                comparison.left = value
                onEdited()
            },
            onDrill = { onSelect(listOf(SelectionStep.Left)) },
        )

        InspectorField(label = "Operator") {
            InspectorOptions(
                options = OperandRules.operatorsFor(
                    left = comparison.left,
                    right = comparison.right,
                    fields = fields,
                ),
                selected = comparison.operator,
                onSelect = { selected ->
                    comparison.operator = selected
                    onEdited()
                },
            )
        }
        InspectorNote(
            text = "Symbolic spellings only: a computed operand takes the value-expression path, and a " +
                "named operator there would be read as a plain field comparison.",
        )

        ComparisonSide(
            title = "Right side",
            operand = comparison.right,
            fields = fields,
            onOperandChanged = { value ->
                comparison.right = value
                onEdited()
            },
            onDrill = { onSelect(listOf(SelectionStep.Right)) },
        )

        ComparisonModifiers(comparison = comparison, fields = fields, onEdited = onEdited)

        InspectorNote(
            text = "Make the left a plain Field and the right a plain Value and this row becomes a " +
                "simple condition again, with the named operators available.",
        )
    }
}

/** One side of a comparison: what kind it is, and the card that drills into it. */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ComparisonSide(
    title: String,
    operand: BuilderOperand,
    fields: BuilderCatalog,
    onOperandChanged: (BuilderOperand) -> Unit,
    onDrill: () -> Unit,
) {
    InspectorSection(title = title)
    OperandKindPicker(
        current = OperandRules.kindOf(operand = operand),
        onSelect = { kind ->
            onOperandChanged(
                OperandRules.defaultOperand(kind = kind, fields = fields, previous = operand),
            )
            onDrill()
        },
    )
    OperandCard(operand = operand, onDrill = onDrill)
}

/** `not` and `ignoreCase` for a comparison row. */
@Suppress("FunctionNaming")
@Composable
private fun ComparisonModifiers(
    comparison: MutableBuilderComparison,
    fields: BuilderCatalog,
    onEdited: () -> Unit,
) {
    InspectorSection(title = "Modifiers")
    InspectorToggle(
        label = "not",
        checked = comparison.negated,
        onCheckedChange = { value ->
            comparison.negated = value
            onEdited()
        },
        hint = "negate this row",
    )
    InspectorToggle(
        label = "ignoreCase",
        checked = comparison.ignoreCase,
        enabled = OperandRules.supportsIgnoreCase(
            left = comparison.left,
            right = comparison.right,
            fields = fields,
        ),
        onCheckedChange = { value ->
            comparison.ignoreCase = value
            onEdited()
        },
        hint = "the only way to compare a computed text value without regard to case",
    )
}

/** A parenthesised group: how it joins, whether it is negated, and what is inside it. */
@Suppress("FunctionNaming")
@Composable
internal fun GroupEditor(
    group: MutableConditionNode.Group,
    state: BuilderEditorState,
    onEdited: () -> Unit,
    onSelectNode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        InspectorField(label = "Join to the row above") {
            InspectorOptions(
                options = listOf("and", "or"),
                selected = group.joinToPrevious.ifBlank { "and" },
                onSelect = { selected ->
                    group.joinToPrevious = selected
                    onEdited()
                },
            )
        }
        InspectorToggle(
            label = "not",
            checked = group.negated,
            onCheckedChange = { value ->
                group.negated = value
                onEdited()
            },
            hint = "negate the whole group",
        )
        InspectorSection(title = "${group.nodes.size} conditions inside")
        group.nodes.forEach { node ->
            InspectorLine(
                text = nodeSummary(node = node),
                selected = false,
                onClick = { onSelectNode(node.id) },
            )
        }
        InspectorActions {
            TinyButton(
                text = "Ungroup",
                onClick = {
                    state.ungroup(id = group.id)
                    onEdited()
                },
            )
            TinyButton(
                text = "+ condition inside",
                onClick = {
                    state.addConditionInside(groupId = group.id)
                    onEdited()
                },
            )
        }
        if (group.nodes.isEmpty()) {
            InspectorNote(
                text = "An empty group renders as () and does not parse — it is dropped when its last " +
                    "row goes.",
                warning = true,
            )
        }
    }
}

/**
 * Switches a side of a simple condition to a computed kind, promoting the row.
 *
 * Refuses — with the reason — when the operator has no value-expression spelling, rather than
 * reinterpreting it. `equals` is the one that converts silently, because `==` means the same thing.
 */
@Suppress("LongParameterList")
private fun promoteSide(
    condition: MutableBuilderCondition,
    state: BuilderEditorState,
    fields: BuilderCatalog,
    kind: OperandRules.OperandKind,
    side: SelectionStep,
    onEdited: () -> Unit,
    onSelect: (List<SelectionStep>) -> Unit,
    onMessage: (String) -> Unit,
) {
    val staysSimple = (side == SelectionStep.Left && kind == OperandRules.OperandKind.FIELD) ||
        (side == SelectionStep.Right && kind == OperandRules.OperandKind.VALUE)
    if (staysSimple) {
        return
    }
    val blocked = RowForm.blockedPromotion(condition = condition)
    if (blocked != null) {
        onMessage(blocked)
        return
    }
    val comparison = RowForm.toComparison(condition = condition)
    val previous = if (side == SelectionStep.Left) comparison.left else comparison.right
    val replacement = OperandRules.defaultOperand(kind = kind, fields = fields, previous = previous)
    if (side == SelectionStep.Left) {
        comparison.left = replacement
    } else {
        comparison.right = replacement
    }
    val replaced = state.replaceNode(
        id = condition.id,
        replacement = MutableConditionNode.ComparisonLeaf(inner = comparison),
    )
    if (replaced) {
        onEdited()
        onSelect(listOf(side))
    }
}

private fun conditionEcho(condition: MutableBuilderCondition): String {
    val not = if (condition.negated) "not " else ""
    val ignoreCase = if (condition.ignoreCase) " ignoreCase" else ""
    val value = when {
        condition.listItems.isNotEmpty() -> condition.listItems.joinToString(
            separator = ", ",
            prefix = "[",
            postfix = "]",
        ) { item -> "\"$item\"" }

        condition.valueTo.isNotBlank() -> "${condition.value} ${condition.valueTo}"
        else -> condition.value
    }
    return "$not${condition.field} ${condition.operator} $value$ignoreCase"
}

private fun comparisonEcho(comparison: MutableBuilderComparison): String {
    val not = if (comparison.negated) "not " else ""
    val ignoreCase = if (comparison.ignoreCase) " ignoreCase" else ""
    val left = OperandText.toDsl(operand = comparison.left)
    val right = OperandText.toDsl(operand = comparison.right)
    return "$not$left ${comparison.operator} $right$ignoreCase"
}

private fun nodeSummary(node: MutableConditionNode): String {
    return when (node) {
        is MutableConditionNode.Leaf ->
            "${node.inner.field} ${node.inner.operator} ${node.inner.value}"

        is MutableConditionNode.ComparisonLeaf ->
            OperandText.toLabel(operand = node.inner.left) + " ${node.inner.operator} " +
                OperandText.toLabel(operand = node.inner.right)

        is MutableConditionNode.Group -> "( ${node.nodes.size} conditions )"
    }
}
