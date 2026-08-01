package ui.tester

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AccentGreen
import ui.AccentRed
import ui.Bg
import ui.TextMuted
import ui.TextPrimary
import ui.tester.model.TraceRow

/**
 * Renders a list of [TraceRow] items produced by the evaluator.
 * Each row shows a coloured dot (green = true, red = false) and the condition label.
 *
 * The implementation is a simple [Column] rather than a [androidx.compose.foundation.lazy.LazyColumn] so that it can be
 * safely embedded inside another scrolling container without creating nested scrollable
 * constraints.
 */
@Suppress("FunctionNaming")
@Composable
fun TraceView(
    rows: List<TraceRow>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = Bg)
            .padding(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        Text(
            text = "Trace",
            style = MaterialTheme.typography.caption,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        rows.forEach { row ->
            // A literal true/false read. Whether a false condition here is expected or a problem is
            // said by the rule row above, which is green, yellow or red for the rule as a whole.
            val dotColor = if (row.result) AccentGreen else AccentRed
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Spacer(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TextPrimary,
                    ),
                )
                // The value that was actually there. Without it a red row says a condition failed but
                // not by how much, which is most of what you want from a trace.
                row.actual?.let { actual ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "was $actual",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextMuted,
                        ),
                    )
                }
            }
        }
    }
}
