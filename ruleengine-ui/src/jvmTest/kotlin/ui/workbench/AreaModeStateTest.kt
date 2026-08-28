package ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
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
 * Which tab an area is on is workbench state, and it has to survive leaving the area.
 *
 * It did before this too, but by accident of where the state was parked: the Schema and Actions areas
 * kept their mode inside `YamlModelSync`, the Manifest area inside a `remember`, and the view model
 * held three mode fields that nothing read. This drives the real app to prove the surviving owner is
 * the view model.
 */
class AreaModeStateTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an area comes back to the tab it was left on`() {
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        RuleEditor(closeController = AppCloseController())
                    }
                }
            }

            onNodeWithText(text = "Schema").performClick()
            waitForIdle()
            // Substring: a tab reads "{ } Code" now — the glyph is part of the label the header draws.
            onNodeWithText(text = "Code", substring = true).performClick()
            waitForIdle()
            assertTrue(
                actual = onAllNodesWithText(text = "Auto-reloads on valid YAML").fetchSemanticsNodes().isNotEmpty(),
                message = "the Schema area did not switch to its text tab",
            )

            // Away, and back. The Actions area has a Code tab of its own, so this also proves the two
            // areas do not share one mode.
            onNodeWithText(text = "Actions").performClick()
            waitForIdle()
            onNodeWithText(text = "Manifest").performClick()
            waitForIdle()
            onNodeWithText(text = "Schema").performClick()
            waitForIdle()

            assertTrue(
                actual = onAllNodesWithText(text = "Auto-reloads on valid YAML").fetchSemanticsNodes().isNotEmpty(),
                message = "the Schema area forgot it was on its text tab",
            )
        }
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
    }
}
