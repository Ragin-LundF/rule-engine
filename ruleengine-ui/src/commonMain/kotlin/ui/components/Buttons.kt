package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BorderColor
import ui.PrimaryBlue
import ui.PrimaryGlow
import ui.TextOnPrimary
import ui.TextSecondary

/**
 * Primary action button: filled pill shape, high emphasis.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp)),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = PrimaryBlue,
            contentColor = TextOnPrimary,
            disabledBackgroundColor = BgElevated,
            disabledContentColor = TextSecondary,
        ),
    ) {
        ButtonLabel(text = text)
    }
}

/**
 * Secondary outlined button: medium emphasis, subtle border.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp)),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryBlue,
            disabledContentColor = TextSecondary,
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (enabled) BorderColor else BorderColor.copy(alpha = 0.5f),
        ),
    ) {
        ButtonLabel(text = text)
    }
}

/**
 * A button's caption.
 *
 * Single-line and non-wrapping, which is not cosmetic: a `Row` of buttons narrower than their
 * combined width squeezes the last one, and a wrapping caption then renders one letter per line —
 * a column of characters where a button should be. Refusing to wrap keeps the button a button, and
 * the toolbar that holds it scrolls instead.
 */
@Suppress("FunctionNaming")
@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.button,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Compact pill button for toolbars.
 */
@Composable
fun ToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    if (primary) {
        PrimaryButton(
            text = label,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        SecondaryButton(
            text = label,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        )
    }
}

/**
 * Small add/remove/action chip that behaves like a button.
 */
@Composable
fun TinyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val bg = if (primary) PrimaryGlow else Color.Transparent
    val borderColor = if (primary) PrimaryBlue.copy(alpha = 0.5f) else BorderColor
    val textColor = if (primary) PrimaryBlue else TextSecondary

    Row(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = bg)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(size = 6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption,
            color = textColor,
        )
    }
}
