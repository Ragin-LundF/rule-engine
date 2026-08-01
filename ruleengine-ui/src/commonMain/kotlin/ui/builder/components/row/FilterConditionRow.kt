package ui.builder.components.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import ui.builder.components.dropdown.DropdownSelector
import ui.builder.components.dropdown.UNKNOWN_MARKER
import ui.builder.model.BuilderFilter
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.OperatorOptions
import ui.components.TinyButton

/**
 * A single restriction of a filtered path segment: element field, operator, value — bracketed like the
 * `[...]` it generates.
 *
 * The field dropdown lists the members of the element being filtered, so names here refer to element
 * fields rather than top-level fields — matching how the engine evaluates filter segments. Only the
 * value is typed; a field name is always picked from the schema.
 */
@Composable
fun FilterConditionRow(
    filter: BuilderFilter,
    fieldOptions: List<CatalogFieldInfo>,
    onFilterChanged: (BuilderFilter) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "[",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )

        if (fieldOptions.isEmpty()) {
            // The element declares no members, so there is nothing valid to offer — and nothing to
            // type either. The name the rule text carried is kept and marked instead.
            Text(
                text = "${filter.field} $UNKNOWN_MARKER",
                style = MaterialTheme.typography.body2,
                color = AccentOrange,
                modifier = Modifier.width(width = 110.dp),
            )
        } else {
            DropdownSelector(
                selected = filter.field,
                options = fieldOptions.map { it.id },
                onSelected = { onFilterChanged(filter.copy(field = it)) },
                modifier = Modifier.width(width = 110.dp),
            )
        }

        DropdownSelector(
            selected = filter.operator,
            options = OperatorOptions.FILTER_OPERATORS,
            onSelected = { onFilterChanged(filter.copy(operator = it)) },
            modifier = Modifier.width(width = 80.dp),
        )

        // The value is a literal, not a field name, so it stays free text.
        PlainTextField(
            value = filter.value,
            placeholder = "value",
            onValueChange = { onFilterChanged(filter.copy(value = it)) },
            modifier = Modifier.width(width = 110.dp),
        )

        Text(
            text = "]",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )

        TinyButton(text = "×", onClick = onRemove)
    }
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
