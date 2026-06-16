package ui.diagrams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Content shown when no rules are available. */
@Composable
internal fun EmptyDiagramPlaceholderContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text  = "⬡",
            style = TextStyle(fontSize = 40.sp, color = BorderRule),
        )
        Text(
            text  = "No valid rules to display",
            style = MaterialTheme.typography.body1,
            color = TextDesc,
        )
        Text(
            text  = "Write a rule in the Code tab and it will appear here",
            style = MaterialTheme.typography.caption,
            color = TextDesc.copy(alpha = 0.6f),
        )
    }
}


