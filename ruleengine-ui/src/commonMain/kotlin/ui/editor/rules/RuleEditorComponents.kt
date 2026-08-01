package ui.editor.rules

import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.TextSecondary

enum class StatusKind { IDLE, SUCCESS, ERROR }

fun Modifier.drawTopLine(w: Dp, color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(x = 0f, y = 0f),
        end = Offset(x = size.width, y = 0f),
        strokeWidth = w.toPx()
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.subtitle1,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
