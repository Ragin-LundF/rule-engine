package ui.workbench.workspace

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The rule workspace's column widths, and the tags a test finds each column by.
 *
 * Three columns, and only the middle one is elastic. The navigator and the context column are given
 * these widths and never measure their content; the workspace takes what is left, clips to it and
 * scrolls inside it. That is the whole of the no-overlap guarantee: the HTML prototype measured its
 * centre column from its content and painted it over the context column below 1920 px, and a
 * `Row` with an intrinsically sized middle child fails the same way. `WorkspaceLayoutTest` checks it at
 * Full HD, at Full HD with 125 % scaling and at 1280 × 720.
 */
object WorkspaceLayout {

    /** The navigator: wide enough for a rule id and two variable chips on a line. */
    val NAVIGATOR_WIDTH: Dp = 264.dp

    /** The context column. Fixed, so a long sample value truncates instead of widening it. */
    val CONTEXT_WIDTH: Dp = 340.dp

    /** Either side column folded to a strip. */
    val COLLAPSED_WIDTH: Dp = 36.dp

    const val NAVIGATOR_TAG: String = "workspace-navigator"
    const val WORKSPACE_TAG: String = "workspace-centre"
    const val CONTEXT_TAG: String = "workspace-context"
}
