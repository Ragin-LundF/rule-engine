package ui.workbench

import androidx.compose.runtime.Composable

/**
 * What the Rules area hands the Visual tab to build its workspace from.
 *
 * One parameter rather than four on every function between `RulesAreaContent` and the canvas: the
 * centre panel only passes these through, and four more pass-through parameters on each of three
 * functions is how the next one is forgotten on one of them.
 *
 * Not a `data class`: it carries composable slots, and equality over a lambda means nothing.
 *
 * @param contextBar   The bar above the canvas — trail, editing mode, checks.
 * @param inlineEditor The editor for the current selection, drawn under the selected row.
 * @param canvasFrame  Wraps the canvas; the Rules area puts the drawer under it here.
 * @param onEscape     Up one level, for `Esc` anywhere in the workspace.
 */
class RuleWorkspaceSlots(
    val contextBar: @Composable () -> Unit = {},
    val inlineEditor: @Composable () -> Unit = {},
    val canvasFrame: @Composable (canvas: @Composable () -> Unit) -> Unit = { canvas -> canvas() },
    val onEscape: () -> Unit = {},
)
