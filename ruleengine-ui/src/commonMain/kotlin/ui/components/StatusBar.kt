package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.TextSecondary
import ui.workbench.ValidationState

/**
 * Bottom status bar showing the current validation state and a summary message.
 */
@Composable
fun BottomStatusBar(
    validationState: ValidationState,
    diagnosticSummary: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        val (dot, label) = validationState.statusLabel()
        Text(
            text = dot,
            style = MaterialTheme.typography.caption,
            color = validationState.statusColor(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption,
            color = validationState.statusColor(),
        )
        if (diagnosticSummary.isNotBlank()) {
            Text(
                text = "•",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
            Text(
                text = diagnosticSummary,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}

private fun ValidationState.statusLabel(): Pair<String, String> = when (this) {
    ValidationState.IDLE -> "○" to "Ready"
    ValidationState.VALIDATING -> "◌" to "Validating…"
    ValidationState.VALID -> "✓" to "Validation passed"
    ValidationState.INVALID -> "✗" to "Validation failed"
}

private fun ValidationState.statusColor() = when (this) {
    ValidationState.IDLE -> TextSecondary
    ValidationState.VALIDATING -> AccentOrange
    ValidationState.VALID -> AccentGreen
    ValidationState.INVALID -> AccentRed
}
