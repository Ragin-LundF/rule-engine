package ui.builder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.BgSurface
import ui.BorderColor
import ui.TextMuted
import ui.TextSecondary

/**
 * A bordered, titled panel used by the inline operand editors.
 *
 * [detail] echoes the DSL the panel currently generates, so what the controls produce is verifiable
 * without switching to Code mode.
 *
 * Named apart from [ui.components.PanelCard], which is a different component: that one is an
 * untitled `Surface` used for general panels, this one is the builder's titled sub-panel. They were
 * both called `PanelCard`, which made it a coin toss which of them an import resolved to.
 */
@Suppress("FunctionNaming")
@Composable
internal fun TitledPanelCard(
    title: String,
    modifier: Modifier = Modifier,
    detail: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(size = 6.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 6.dp))
            .padding(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption,
                color = TextSecondary,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.caption,
                    color = TextMuted,
                )
            }
        }
        content()
    }
}
