package ui.editor.rules.sections

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
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
 * The application bar, and the thing it could not do before: fit.
 *
 * It held eight text buttons in a fixed row — New / Open / Save / Save As / Save Schema As / Save
 * Actions As / Inspector / theme — with no collapse behaviour, so below roughly 1300 px it simply ran
 * out of room. These drive the real app at two widths and check that the rare actions stay reachable
 * at both.
 */
class TopBarSectionTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a wide window shows the labels`() {
        runApp(width = WIDE) {
            assertPresent(text = "Rule Engine")
            assertPresent(text = "WORKBENCH")
            assertPresent(text = "Project ▼")
            assertPresent(text = "Inspector")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a narrow window gives up its identity, never its controls`() {
        runApp(width = NARROW) {
            // The wordmark and the badge are what a narrow bar gives up first.
            assertAbsent(text = "Rule Engine")
            assertAbsent(text = "WORKBENCH")

            // The controls stay as they are. They are words, not glyphs: every symbol that would have
            // stood in for them at this size reads as a hairline.
            assertPresent(text = "Project ▼")
            assertPresent(text = "Save")
            assertPresent(text = "Inspector")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the shared-file exports are one menu away at any width`() {
        // They were two top-level buttons, and they are the two rarest things the bar could offer.
        runApp(width = NARROW) {
            onNodeWithText(text = "▼").performClick()
            waitForIdle()

            assertPresent(text = "Save Schema As…")
            assertPresent(text = "Save Actions As…")
            assertPresent(text = "Save Project As…")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an untouched project shows no unsaved marker`() {
        runApp(width = WIDE) {
            assertAbsent(text = "UNSAVED")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertPresent(text: String) {
        assertTrue(
            actual = onAllNodesWithText(text = text).fetchSemanticsNodes().isNotEmpty(),
            message = "expected \"$text\" in the application bar",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertAbsent(text: String) {
        assertTrue(
            actual = onAllNodesWithText(text = text).fetchSemanticsNodes().isEmpty(),
            message = "did not expect \"$text\" in the application bar",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runApp(width: Int, assertions: DesktopComposeUiTest.() -> Unit) {
        runDesktopComposeUiTest(width = width, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        RuleEditor(closeController = AppCloseController())
                    }
                }
            }
            assertions()
        }
    }

    private companion object {
        const val WIDE = 1440
        const val NARROW = 900
        const val HEIGHT = 900
    }
}
