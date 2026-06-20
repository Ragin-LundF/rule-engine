package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgSurface
import ui.BorderColor
import ui.TextSecondary
import ui.workbench.ValidationState

/**
 * Modern compact bottom status bar.
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
            .clip(shape = RoundedCornerShape(size = 10.dp))
            .background(color = BgSurface)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 10.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size = 8.dp)
                .background(color = validationState.statusColor(), shape = CircleShape),
        )
        val label = validationState.statusLabel()
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

private fun ValidationState.statusLabel(): String = when (this) {
    ValidationState.IDLE -> "Ready"
    ValidationState.VALIDATING -> "Validating…"
    ValidationState.VALID -> "Validation passed"
    ValidationState.INVALID -> "Validation failed"
}

private fun ValidationState.statusColor() = when (this) {
    ValidationState.IDLE -> TextSecondary
    ValidationState.VALIDATING -> AccentOrange
    ValidationState.VALID -> AccentGreen
    ValidationState.INVALID -> AccentRed
}
