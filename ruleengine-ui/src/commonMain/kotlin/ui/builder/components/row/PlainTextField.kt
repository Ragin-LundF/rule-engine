package ui.builder.components.row

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import ui.BgElevated
import ui.BorderColor
import ui.TextPrimary
import ui.TextSecondary

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
    /** False for a schema the visual editor will not rewrite — see `SchemaEditorState.isReadOnly`. */
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
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
