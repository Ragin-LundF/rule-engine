package ui.editor.rules.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import ui.BorderColor
import ui.BgSurface
import ui.editor.rules.PanelDivider
import ui.editor.rules.RuleEditorState

/** Right panel: header toolbar, panel divider, and the main code/diagram editor. */
@Suppress("FunctionNaming")
@Composable
fun RightPanelSection(state: RuleEditorState, scope: CoroutineScope, modifier: Modifier = Modifier) {
    // The graphics layer is shared between the header (Export PNG) and the diagram view (capture).
    val diagramGraphicsLayer = rememberGraphicsLayer()

    Column(
        modifier = modifier
            .clip(shape = RoundedCornerShape(size = 8.dp))
            .background(color = BgSurface)
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(size = 8.dp))
            .padding(all = 14.dp),
    ) {
        // ── Header: title + view-mode toggle + action buttons ─────
        RightPanelHeaderSection(
            state = state,
            scope = scope,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )

        PanelDivider()

        // ── Code Editor or Diagram view ───────────────────────────
        MainEditorContentSection(
            state = state,
            diagramGraphicsLayer = diagramGraphicsLayer,
        )
    }
}




