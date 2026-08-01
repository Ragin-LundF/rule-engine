package ui.builder.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import ui.PrimaryBlue
import ui.TextSecondary

/**
 * The case-insensitivity flag on a comparison.
 *
 * Shared by the simple condition row and the advanced comparison row, which had byte-identical
 * copies: the two rows edit the same flag on the same rule, so they have to look the same.
 */
@Suppress("FunctionNaming")
@Composable
internal fun IgnoreCaseToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue),
        )
        Text(
            text = "ignore case",
            style = MaterialTheme.typography.caption,
            color = TextSecondary,
        )
    }
}
