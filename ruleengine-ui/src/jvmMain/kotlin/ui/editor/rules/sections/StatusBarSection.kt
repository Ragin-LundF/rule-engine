package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.AccentGreen
import ui.AccentRed
import ui.BorderColor
import ui.BgSurface
import ui.TextMuted
import ui.TextSecondary
import ui.editor.rules.RuleEditorState
import ui.editor.rules.StatusKind
import ui.editor.rules.drawTopLine

/** Status bar: shows the current status message with a colour-coded indicator dot and schema info. */
@Suppress("FunctionNaming")
@Composable
fun StatusBarSection(state: RuleEditorState) {
    val statusKind by state.statusKind
    val status by state.status
    val parsedSchema by state.parsedSchema

    Surface(color = BgSurface, elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawTopLine(w = 1.dp, color = BorderColor)
                .padding(horizontal = 18.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dot = when (statusKind) {
                StatusKind.SUCCESS -> AccentGreen
                StatusKind.ERROR -> AccentRed
                StatusKind.IDLE -> TextMuted
            }
            Box(Modifier.size(7.dp).background(color = dot, shape = CircleShape))
            Text(status, style = MaterialTheme.typography.caption, color = TextSecondary)
            Spacer(Modifier.weight(1f))
            parsedSchema?.let { Text("Schema: ${it.fields.size} fields", style = MaterialTheme.typography.caption) }
        }
    }
}


