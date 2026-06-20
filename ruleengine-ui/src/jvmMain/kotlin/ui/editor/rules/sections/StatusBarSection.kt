package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentOrange
import ui.AccentRed
import ui.BgElevated
import ui.BorderColor
import ui.TextMuted
import ui.TextPrimary
import ui.TextSecondary
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind

/** Status bar: shows the current status message with a colour-coded indicator dot and schema info. */
@Suppress("FunctionNaming")
@Composable
fun StatusBarSection(state: RuleEditorState) {
    val statusKind by state.statusKind
    val status by state.status
    val parsedSchema by state.parsedSchema

    Spacer(modifier = Modifier.height(height = 8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 10.dp))
            .background(color = BgElevated)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        val dot = when (statusKind) {
            StatusKind.SUCCESS -> AccentGreen
            StatusKind.ERROR -> AccentRed
            StatusKind.IDLE -> TextMuted
        }
        val messageColor = when (statusKind) {
            StatusKind.SUCCESS -> AccentGreen
            StatusKind.ERROR -> AccentRed
            StatusKind.IDLE -> TextSecondary
        }
        Box(
            modifier = Modifier
                .size(size = 8.dp)
                .background(color = dot, shape = CircleShape),
        )
        Text(
            text = status,
            style = MaterialTheme.typography.caption,
            color = messageColor,
        )
        Spacer(modifier = Modifier.weight(weight = 1f))
        parsedSchema?.let {
            Text(
                text = "Schema: ${it.fields.size} fields",
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
        }
    }
}
