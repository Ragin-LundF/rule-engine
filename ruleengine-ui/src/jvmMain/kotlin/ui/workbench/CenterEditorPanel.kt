package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import ui.editor.rules.RuleEditorState
import ui.editor.rules.ViewMode
import ui.editor.rules.sections.RightPanelSection

/**
 * Center panel that dispatches to the correct mode view based on [RuleEditorState.viewMode]:
 * - [ViewMode.CODE] and [ViewMode.DIAGRAM] → existing [RightPanelSection] (preserves all behavior).
 * - [ViewMode.BUILDER] → [BuilderModePlaceholder] until the visual builder is implemented.
 *
 * Switching tabs never discards the rule text because [RuleEditorState] holds it independently.
 */
@Composable
fun CenterEditorPanel(
    state: RuleEditorState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val viewMode = state.viewMode.value
    if (viewMode == ViewMode.BUILDER) {
        BuilderModePlaceholder(modifier = modifier)
    } else {
        // CODE and DIAGRAM are both handled inside RightPanelSection via its internal ViewMode check.
        RightPanelSection(state = state, scope = scope, modifier = modifier)
    }
}
