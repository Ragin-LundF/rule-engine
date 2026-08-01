package ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import ruleengine.dsl.ast.RuleAst
import ui.AppTheme
import ui.Bg
import ui.editor.rules.RuleEditorState

/**
 * The diagram in its own window, opened by the "⤢ Expand" button in diagram mode.
 *
 * Reads the same [state] as the inline canvas, so it updates live while the rules are edited.
 * [AppTheme] has to be applied again: a new window is a separate composition and does not inherit
 * the main window's theme.
 */
@Suppress("FunctionNaming")
@Composable
fun ExpandedDiagramWindow(state: RuleEditorState, rules: List<RuleAst>) {
    Window(
        onCloseRequest = { state.showExpandedDiagram.value = false },
        title = "Rule Diagram — Full View",
        state = rememberWindowState(size = DpSize(width = 1400.dp, height = 900.dp)),
    ) {
        AppTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Bg,
            ) {
                DiagramModeHost(
                    view = state.diagramView.value,
                    data = diagramDataFor(state = state, rules = rules),
                )
            }
        }
    }
}
