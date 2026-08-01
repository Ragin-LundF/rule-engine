package ui.diagrams.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import ui.diagrams.ConnectorW
import ui.diagrams.LineColor

/** Draws a short vertical line with an arrowhead to visually connect nodes. */
@Composable
internal fun VerticalConnector() {
    Box(
        modifier         = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.width(20.dp).height(28.dp)) {
            val cx        = size.width / 2f
            val arrowTip  = size.height - 4f
            val arrowSize = 5f

            drawLine(
                color       = LineColor,
                start       = Offset(x = cx, y = 0f),
                end         = Offset(x = cx, y = arrowTip),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
            drawLine(
                color       = LineColor,
                start       = Offset(x = cx - arrowSize, y = arrowTip - arrowSize),
                end         = Offset(x = cx, y = arrowTip),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
            drawLine(
                color       = LineColor,
                start       = Offset(x = cx + arrowSize, y = arrowTip - arrowSize),
                end         = Offset(x = cx, y = arrowTip),
                strokeWidth = ConnectorW.toPx(),
                cap         = StrokeCap.Round,
            )
        }
    }
}

