package ui.workbench.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppCloseController
import ui.AppTheme
import ui.Bg
import ui.RuleEditor
import ui.builder.outline.INLINE_EDITOR_TAG
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The centre column never paints over the context column, at any of the window sizes the app is used at.
 *
 * This is the review finding the redesign was approved on: the HTML prototype measured its centre column
 * from its content and, below 1920 px, painted it over the right-hand column. So the test drives the real
 * app into the widest thing the workspace shows — the complex sample rule, its nested condition 2.1
 * selected, and the computed value's editor open under it — and checks every node in the centre column
 * against the context column's bounds.
 *
 * `boundsInRoot` is clipped by every ancestor, which is what makes this the right check: it is the area a
 * node can actually paint into. A centre column that stopped clipping would report content running past
 * its edge, and that is the overlap a reader sees.
 */
class WorkspaceLayoutTest {

    @Test
    fun `nothing in the workspace overlaps the context column at Full HD`() {
        assertNoOverlap(width = 1_920, height = 1_080)
    }

    @Test
    fun `nothing in the workspace overlaps the context column at Full HD with 125 percent scaling`() {
        assertNoOverlap(width = 1_536, height = 864)
    }

    @Test
    fun `nothing in the workspace overlaps the context column at 1280 x 720`() {
        assertNoOverlap(width = 1_280, height = 720)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun assertNoOverlap(width: Int, height: Int) {
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        RuleEditor(closeController = AppCloseController())
                    }
                }
            }

            loadSample(name = SAMPLE_NAME)
            openComplexCondition()

            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val context = onNodeWithTag(testTag = WorkspaceLayout.CONTEXT_TAG).fetchSemanticsNode().boundsInRoot
            val centre = onNodeWithTag(testTag = WorkspaceLayout.WORKSPACE_TAG).fetchSemanticsNode().boundsInRoot

            assertTrue(
                actual = context.right <= root.right && context.width > 0f,
                message = "at ${width}x$height the context column is pushed off the window: $context in $root",
            )
            assertTrue(
                actual = centre.width > 0f && !centre.overlaps(other = context),
                message = "at ${width}x$height the workspace $centre overlaps the context column $context",
            )

            val offenders = onAllNodes(
                matcher = hasAnyAncestor(matcher = hasTestTag(testTag = WorkspaceLayout.WORKSPACE_TAG)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
                .filter { node -> node.boundsInRoot.isPainted() && node.boundsInRoot.overlaps(other = context) }
            assertTrue(
                actual = offenders.isEmpty(),
                message = "at ${width}x$height ${offenders.size} workspace node(s) overlap the context column " +
                    "$context, e.g. ${offenders.take(n = 3).map { node -> node.boundsInRoot }}",
            )
        }
    }

    /** The complex rule, condition 2.1's computed left value selected, and its editor open under the row. */
    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.openComplexCondition() {
        // A sample opens in Code mode; the workspace is the Visual tab.
        onNodeWithContentDescription(label = "Visual").performClick()
        waitForIdle()

        onNode(
            matcher = hasText(text = COMPLEX_RULE) and
                hasAnyAncestor(matcher = hasTestTag(testTag = WorkspaceLayout.NAVIGATOR_TAG)),
        ).performScrollTo().performClick()
        waitForIdle()

        // The first `sum(` token in the workspace is condition 2.1's left value: condition 1 reads a
        // variable, and 2.1 is the first row of the group.
        onAllNodes(
            matcher = hasText(text = "sum(") and
                hasAnyAncestor(matcher = hasTestTag(testTag = WorkspaceLayout.WORKSPACE_TAG)),
        )[0].performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(testTag = INLINE_EDITOR_TAG).assertExists()
    }

    /**
     * Loads one sample by name: the gallery is a lazy grid, so the card is scrolled into view first, and
     * its button is the "Load Sample" closest below its title in the same column of the grid.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.loadSample(name: String) {
        onNodeWithText(text = "Samples").performClick()
        waitForIdle()
        onNode(matcher = hasScrollToIndexAction()).performScrollToNode(matcher = hasText(text = name))
        waitForIdle()

        val title = onNodeWithText(text = name).fetchSemanticsNode().boundsInRoot
        val halfWidth = onRoot().fetchSemanticsNode().boundsInRoot.width / 2
        val buttons = onAllNodesWithText(text = LOAD_SAMPLE).fetchSemanticsNodes().map { node -> node.boundsInRoot }
        // Below the title, right of its left edge, and less than half the window further right: the card's
        // own button, never the one in the neighbouring column of the grid.
        val index = buttons.indices
            .filter { i -> buttons[i].top > title.top && buttons[i].left > title.left }
            .filter { i -> buttons[i].left - title.left < halfWidth }
            .minBy { i -> buttons[i].top }
        onAllNodesWithText(text = LOAD_SAMPLE)[index].performClick()
        waitForIdle()

        onAllNodesWithText(text = LOAD_SAMPLE).onLast().performClick()
        waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            onAllNodesWithText(text = LOAD_SAMPLE).fetchSemanticsNodes().isEmpty()
        }
        waitForIdle()
    }

    private fun Rect.isPainted(): Boolean {
        return width > 0f && height > 0f
    }

    private companion object {
        const val SAMPLE_NAME = "Loan Decisioning"
        const val COMPLEX_RULE = "refer-concentrated-debt"
        const val LOAD_SAMPLE = "Load Sample"
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
