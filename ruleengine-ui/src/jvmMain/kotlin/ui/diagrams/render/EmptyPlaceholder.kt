package ui.diagrams.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.diagrams.BorderRule
import ui.diagrams.TextDesc

/** Content shown when no rules are available. */
@Composable
internal fun EmptyDiagramPlaceholderContent() {
    DiagramPlaceholderContent(
        headline = "No valid rules to display",
        hint = "Write a rule in the Code tab and it will appear here",
    )
}

/**
 * The same placeholder with its wording supplied by the caller.
 *
 * Each view is empty for a different reason and can say what would fill it — a run view with no
 * manifest loaded is a different situation from a rule file that does not parse, and telling the two
 * apart is the whole value of the message.
 */
@Composable
internal fun DiagramPlaceholderContent(headline: String, hint: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text  = "⬡",
            style = TextStyle(fontSize = 40.sp, color = BorderRule),
        )
        Text(
            text  = headline,
            style = MaterialTheme.typography.body1,
            color = TextDesc,
        )
        Text(
            text  = hint,
            style = MaterialTheme.typography.caption,
            color = TextDesc.copy(alpha = 0.6f),
        )
    }
}

