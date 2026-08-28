package ui.dock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppCloseController
import ui.AppTheme
import ui.Bg
import ui.RuleEditor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Drives the real app and checks the dock actually appears where it should.
 *
 * The unit tests around it cover the clamp, the ranges and the highlight layering, but none of them
 * proves the thing is *wired*: four areas each build their own tabs, and a mistake there is invisible
 * until someone opens that area. This renders the app off-screen — the same way `DocScreenshotsTest`
 * does — and visits every one.
 *
 * Written against visible text rather than test tags because the app has no tags, and adding them only
 * for this would put test scaffolding in four production files. The tab labels are matched as
 * substrings: a tab reads "⊞ Visual" now, glyph included, because the icon is part of the label the
 * shared header draws.
 *
 * It asserts that each dock is **present**, not whether it starts open: the open state is persisted, so
 * a developer who once collapsed a panel would otherwise fail this test on a clean checkout. The
 * defaults are asserted where they actually live, on the enum, in `DockHeightTest`.
 */
class DockSurfacesTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `every area has a dock, and only the builder starts open`() {
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        RuleEditor(closeController = AppCloseController())
                    }
                }
            }

            loadFirstSample()

            // ── the Builder's two canvases ────────────────────────────────────────────────
            // "Visual" is the tab's name in every area now; the Builder is what it opens in Rules.
            onNodeWithContentDescription(label = "Visual").performClick()
            waitForIdle()
            assertDockPresent(where = "the Builder")
            assertVisible(text = "Checks", where = "the Builder dock's tab strip")

            // The Board is the canvas that had no dock at all before this, and it must be the *same*
            // one rather than a second copy.
            onNodeWithContentDescription(label = "Board").performClick()
            waitForIdle()
            assertDockPresent(where = "the Board")
            assertVisible(text = "Checks", where = "the Board dock's tab strip")
            onNodeWithContentDescription(label = "Outline").performClick()
            waitForIdle()

            // ── Code mode has no dock: the mode *is* the text ──────────────────────────────
            onNodeWithContentDescription(label = "Code").performClick()
            waitForIdle()
            assertNoDock(where = "Code mode, which needs no preview of itself")

            // ── the three YAML areas ──────────────────────────────────────────────────────
            listOf("Schema", "Actions", "Manifest").forEach { area ->
                // The rail button, taken as the first match: the area's own name appears again inside
                // the panel it opens, and the rail is composed before the centre.
                onAllNodesWithText(text = area)[0].performClick()
                waitForIdle()
                assertDockPresent(where = "the $area area")
                assertVisible(text = "Checks", where = "the $area dock's tab strip")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertVisible(text: String, where: String) {
        assertTrue(
            actual = onAllNodesWithText(text = text).fetchSemanticsNodes().isNotEmpty(),
            message = "expected \"$text\" in $where",
        )
    }

    /**
     * The dock's header is always on screen whichever way it is folded, so its toggle is the one thing
     * that says "there is a dock here" without depending on a stored preference.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertDockPresent(where: String) {
        val open = onAllNodesWithText(text = "hide").fetchSemanticsNodes().isNotEmpty()
        val shut = onAllNodesWithText(text = "show").fetchSemanticsNodes().isNotEmpty()
        assertTrue(actual = open || shut, message = "no dock header found in $where")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertNoDock(where: String) {
        val open = onAllNodesWithText(text = "hide").fetchSemanticsNodes().isNotEmpty()
        val shut = onAllNodesWithText(text = "show").fetchSemanticsNodes().isNotEmpty()
        assertTrue(actual = !open && !shut, message = "found a dock header in $where")
    }

    /** Same two-step click the screenshot test uses: the grid's button, then the dialog's. */
    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.loadFirstSample() {
        onNodeWithText(text = "Samples").performClick()
        waitForIdle()
        onAllNodesWithText(text = LOAD_SAMPLE)[0].performClick()
        waitForIdle()
        onAllNodesWithText(text = LOAD_SAMPLE).onLast().performClick()
        waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            onAllNodesWithText(text = LOAD_SAMPLE).fetchSemanticsNodes().isEmpty()
        }
        waitForIdle()
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
        const val LOAD_SAMPLE = "Load Sample"
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
