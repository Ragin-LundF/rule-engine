package ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.TextSecondary

/**
 * Placeholder shown in Builder mode until the visual rule builder is implemented (Step 9+).
 * Displays a friendly message so the tab is functional but clearly not yet editable.
 */
@Composable
fun BuilderModePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(all = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "⊞  Builder",
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface,
        )
        Text(
            text = "Visual rule builder coming soon.\nSwitch to Code mode to edit rules.",
            style = MaterialTheme.typography.body2,
            color = TextSecondary,
        )
    }
}
