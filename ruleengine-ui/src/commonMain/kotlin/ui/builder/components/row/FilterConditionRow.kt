package ui.builder.components.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import ui.AccentOrange
import ui.BgElevated
import ui.BorderColor
import ui.TextPrimary
import ui.TextSecondary
import ui.builder.OperatorOptions
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.dropdown.UNKNOWN_MARKER
import ui.builder.components.editor.NestedOperandEditor
import ui.builder.components.model.ExpandedSide
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderOperand
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.membershipOperand
import ui.builder.model.membershipText
import ui.builder.model.names
import ui.builder.model.pathOperand
import ui.components.TinyButton

/**
 * A single restriction of a filtered path segment — bracketed like the `[...]` it generates.
 *
 * A miniature [ComparisonRowEditor]: both sides are operands, so a restriction may compare an
 * aggregate, arithmetic or a further filtered path, not only a member against a literal. The common
 * case stays one click — a plain member gets a dropdown beside its chip, the same way a literal gets
 * an inline value box — and the chip's kind menu is what reaches the richer shapes.
 *
 * Names here resolve against the element with the document behind it, which is what
 * `OperandRules.filterCatalog` supplies as [fields] and what the engine does at evaluation time.
 * [fieldOptions] is the flat member list for the plain dropdown; the two differ because a chip has to
 * be able to walk into a nested object while the dropdown wants one flat choice per line.
 */
@Suppress("LongParameterList")
@Composable
fun FilterConditionRow(
    filter: BuilderFilter,
    fieldOptions: List<CatalogFieldInfo>,
    fields: List<CatalogFieldInfo>,
    onFilterChanged: (BuilderFilter) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Starts collapsed, which is what bounds the nesting: a filter whose operand holds another
    // filtered path only renders that inner row once the author expands into it, one click per level.
    var expanded by remember { mutableStateOf(value = ExpandedSide.NONE) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "[", style = MaterialTheme.typography.body2, color = TextSecondary)

            FilterLeftSide(
                filter = filter,
                fieldOptions = fieldOptions,
                fields = fields,
                expanded = expanded == ExpandedSide.LEFT,
                onFilterChanged = onFilterChanged,
                onToggleExpanded = {
                    expanded = if (expanded == ExpandedSide.LEFT) ExpandedSide.NONE else ExpandedSide.LEFT
                },
            )

            DropdownSelector(
                selected = filter.operator,
                options = OperatorOptions.FILTER_OPERATORS,
                onSelected = { onFilterChanged(filter.copy(operator = it)) },
                modifier = Modifier.width(width = 80.dp),
            )

            FilterRightSide(
                filter = filter,
                fields = fields,
                expanded = expanded == ExpandedSide.RIGHT,
                onFilterChanged = onFilterChanged,
                onToggleExpanded = {
                    expanded = if (expanded == ExpandedSide.RIGHT) ExpandedSide.NONE else ExpandedSide.RIGHT
                },
            )

            Text(text = "]", style = MaterialTheme.typography.body2, color = TextSecondary)

            TinyButton(text = "×", onClick = onRemove)
        }

        when (expanded) {
            ExpandedSide.LEFT -> NestedOperandEditor(
                operand = filter.left,
                fields = fields,
                onChanged = { onFilterChanged(filter.copy(left = it)) },
            )

            ExpandedSide.RIGHT -> NestedOperandEditor(
                operand = filter.right,
                fields = fields,
                onChanged = { onFilterChanged(filter.copy(right = it)) },
            )

            ExpandedSide.NONE -> Unit
        }
    }
}

/**
 * The compared side of a restriction.
 *
 * A plain single-segment member keeps its dropdown, because picking one from the element's members is
 * what almost every filter does and an expand click for it would be a step backwards. Anything else —
 * an aggregate, arithmetic, a dotted or filtered path — is edited through the chip and its panel.
 */
@Suppress("LongParameterList")
@Composable
private fun FilterLeftSide(
    filter: BuilderFilter,
    fieldOptions: List<CatalogFieldInfo>,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onFilterChanged: (BuilderFilter) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val plainMember = (filter.left as? BuilderOperand.FieldRef)
        ?.takeIf { ref -> ref.path.size == 1 && ref.path.single().decorations.isEmpty() }

    if (plainMember != null && fieldOptions.isEmpty()) {
        // The element declares no members, so there is nothing valid to offer — and nothing to type
        // either. The name the rule text carried is kept and marked instead.
        Text(
            text = "${plainMember.path.names.single()} $UNKNOWN_MARKER",
            style = MaterialTheme.typography.body2,
            color = AccentOrange,
            modifier = Modifier.width(width = 110.dp),
        )
        return
    }

    OperandSide(
        operand = filter.left,
        other = filter.right,
        fields = fields,
        expanded = expanded,
        onOperandChanged = { onFilterChanged(filter.copy(left = it)) },
        onToggleExpanded = onToggleExpanded,
    )

    if (plainMember != null) {
        DropdownSelector(
            selected = plainMember.path.names.single(),
            options = fieldOptions.map { it.id },
            onSelected = { onFilterChanged(filter.copy(left = pathOperand(dotted = it))) },
            modifier = Modifier.width(width = 110.dp),
        )
    }
}

/**
 * The value side of a restriction.
 *
 * `in` keeps a single text box: its right side is a list or the name of one, and which of the two was
 * meant is decided by what the author types rather than by a kind picked first — see
 * [membershipOperand]. Every other operator takes an ordinary operand.
 */
@Composable
private fun FilterRightSide(
    filter: BuilderFilter,
    fields: List<CatalogFieldInfo>,
    expanded: Boolean,
    onFilterChanged: (BuilderFilter) -> Unit,
    onToggleExpanded: () -> Unit,
) {
    if (OperatorOptions.isList(operator = filter.operator)) {
        PlainTextField(
            value = membershipText(operand = filter.right),
            placeholder = "a, b  or  fieldName",
            onValueChange = { text -> onFilterChanged(filter.copy(right = membershipOperand(text = text))) },
            modifier = Modifier.width(width = 150.dp),
        )
        return
    }

    OperandSide(
        operand = filter.right,
        other = filter.left,
        fields = fields,
        expanded = expanded,
        onOperandChanged = { onFilterChanged(filter.copy(right = it)) },
        onToggleExpanded = onToggleExpanded,
    )
}

/**
 * A compact single-line text field matching the height of the surrounding dropdowns.
 *
 * `OutlinedTextField` is too tall for these dense rows, so this is a minimal
 * `BasicTextField` in the same box styling as [DropdownSelector].
 */
@Composable
fun PlainTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.body2.copy(color = TextPrimary),
        cursorBrush = SolidColor(value = TextPrimary),
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgElevated)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.body2,
                        color = TextSecondary,
                    )
                }
                innerTextField()
            }
        },
    )
}
