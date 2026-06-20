package ui.workbench

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import ui.builder.BuilderEditorState
import ui.builder.CatalogFieldInfo
import ui.builder.RuleBuilderView
import ui.editor.rules.RuleEditorState
import ui.editor.rules.ViewMode
import ui.editor.rules.sections.RightPanelSection

/**
 * Center panel that dispatches to the correct mode view based on [RuleEditorState.viewMode]:
 * - [ViewMode.CODE] and [ViewMode.DIAGRAM] → existing [RightPanelSection] (preserves all behavior).
 * - [ViewMode.BUILDER] → [RuleBuilderView] showing the selected rule as editable visual blocks.
 *
 * Switching tabs never discards the rule text because [RuleEditorState] holds it independently.
 */
@Composable
fun CenterEditorPanel(
    state: RuleEditorState,
    scope: CoroutineScope,
    builderEditorState: BuilderEditorState = BuilderEditorState.fromBuilderRule(ui.builder.BuilderRule.None),
    catalogFields: List<CatalogFieldInfo> = emptyList(),
    onBuilderDslChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewMode = state.viewMode.value
    if (viewMode == ViewMode.BUILDER) {
        RuleBuilderView(
            editorState = builderEditorState,
            catalogFields = catalogFields,
            onDslChange = onBuilderDslChange,
            modifier = modifier,
        )
    } else {
        // CODE and DIAGRAM are both handled inside RightPanelSection via its internal ViewMode check.
        RightPanelSection(state = state, scope = scope, modifier = modifier)
    }
}
