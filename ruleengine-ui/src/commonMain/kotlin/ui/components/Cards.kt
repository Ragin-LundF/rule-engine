package ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.BgElevated
import ui.BorderColor

/**
 * A rounded surface card used to group related content in panels.
 */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = MaterialTheme.shapes.medium,
            ),
        color = BgElevated,
        shape = MaterialTheme.shapes.medium,
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            content = content,
        )
    }
}

/**
 * A smaller, more compact card for list items or form sections.
 */
@Composable
fun CompactCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 10.dp))
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(size = 10.dp),
            ),
        color = BgElevated,
        shape = RoundedCornerShape(size = 10.dp),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 12.dp),
            content = content,
        )
    }
}
