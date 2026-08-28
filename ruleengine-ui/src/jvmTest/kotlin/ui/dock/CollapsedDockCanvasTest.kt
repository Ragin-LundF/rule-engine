package ui.dock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import ui.AppTheme
import ui.Bg
import ui.dock.model.DockTab
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The canvas keeps its height whichever state the dock is in.
 *
 * A collapsed dock is measured before the canvas — the canvas takes what is left, by `weight` — so a
 * dock that asks for the whole height while collapsed lays the canvas out at zero, header and tabs and
 * all. That is not a subtle mis-measurement: the area renders as a dock header alone, and every control
 * above it becomes a click target with no area to click. The three YAML areas start collapsed, so it is
 * the state a fresh install opens in.
 *
 * Driven through [CanvasDockScaffold] and the real [EditorDock] rather than through the app, because the
 * app's dock state is persisted: a machine where the panel was once opened would pass either way.
 */
class CollapsedDockCanvasTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a collapsed dock leaves the canvas its height`() {
        assertTrue(
            actual = canvasHeightWith(expanded = false) > 0,
            message = "the collapsed dock took the whole panel and squashed the canvas to zero height",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an expanded dock takes its height from the canvas and no more`() {
        val collapsed = canvasHeightWith(expanded = false)
        val expanded = canvasHeightWith(expanded = true)

        assertTrue(
            actual = expanded in 1 until collapsed,
            message = "expanding the dock gave the canvas $expanded of the $collapsed px it had",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun canvasHeightWith(expanded: Boolean): Int {
        var height = -1
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        CanvasDockScaffold(
                            expanded = expanded,
                            dockHeight = DOCK_HEIGHT.dp,
                            onDockResize = { _, _ -> },
                            dock = {
                                EditorDock(
                                    tabs = listOf(
                                        DockTab(id = "file", title = FILE_TAB) { Text(text = "dock body") },
                                    ),
                                    selectedTabId = "file",
                                    onSelectTab = {},
                                    expanded = expanded,
                                    onToggleExpanded = {},
                                )
                            },
                            canvas = { Text(text = CANVAS, modifier = Modifier.fillMaxSize()) },
                        )
                    }
                }
            }

            // The dock is there in both states, so a header that disappeared would fail here rather
            // than pass as a canvas that got the height by having the panel to itself.
            onNodeWithText(text = FILE_TAB).assertExists()
            height = onNodeWithText(text = CANVAS).fetchSemanticsNode().size.height
        }
        return height
    }

    private companion object {
        const val WIDTH = 900
        const val HEIGHT = 700
        const val DOCK_HEIGHT = 220f
        const val CANVAS = "the canvas"
        const val FILE_TAB = "file.yaml"
    }
}
