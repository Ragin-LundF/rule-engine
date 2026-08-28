package ui.docs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppCloseController
import ui.AppTheme
import ui.Bg
import ui.RuleEditor
import ui.theme.ThemeController
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the screenshots used by `README_UI.md` off-screen and writes them to `docs/assets/ui`.
 *
 * It is a test rather than a tool because the Compose test harness is the only way to drive the real
 * app without a display: it renders the same composable `main()` does, clicks through it, and captures
 * the frame. Nothing is asserted — the images are the output.
 *
 * Skipped unless asked for, because it writes into the repository and font rendering differs per
 * machine, so an unasked-for run would show up as a diff on every checkout:
 *
 * ```
 * ./gradlew :ruleengine-ui:jvmTest -PdocScreenshots=true --tests '*DocScreenshotsTest*'
 * ```
 */
class DocScreenshotsTest {

    private val outputDir = File("../docs/assets/ui")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun generateDocScreenshots() {
        if (System.getProperty("docScreenshots").isNullOrBlank()) return

        outputDir.mkdirs()

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        RuleEditor(closeController = AppCloseController())
                    }
                }
            }

            onNodeWithText(text = "Samples").performClick()
            waitForIdle()
            capture(name = "sample-gallery")

            loadFirstSample()

            // A sample opens in Code mode, so the Builder is one click away rather than already there.
            onNodeWithText(text = "Builder").performClick()
            waitForIdle()
            capture(name = "rule-builder")

            // The board is a canvas inside the Builder, not a mode tab, so its switch is on the canvas.
            onNodeWithText(text = "Board").performClick()
            waitForIdle()
            capture(name = "rule-board")

            onNodeWithText(text = "Outline").performClick()
            waitForIdle()

            onNodeWithText(text = "Code").performClick()
            waitForIdle()
            capture(name = "code-view")

            onNodeWithText(text = "Diagram").performClick()
            waitForIdle()
            capture(name = "diagram-rule-trees")

            onNodeWithText(text = "Rule trees", substring = true).performClick()
            waitForIdle()
            onNodeWithText(text = "Manifest run").performClick()
            waitForIdle()
            capture(name = "diagram-manifest-run")

            captureLightMode()
        }
    }

    /**
     * The code view again, in the light palette, so the docs can show both.
     *
     * [ThemeController] is set directly rather than by clicking the top bar's ☀ button, because that
     * button also persists the choice — a screenshot run would leave the developer's own editor in
     * light mode. Restored afterwards for the same reason, since the controller is a global.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.captureLightMode() {
        onNodeWithText(text = "Code").performClick()
        waitForIdle()
        ThemeController.isDark = false
        try {
            waitForIdle()
            capture(name = "code-view-light")
        } finally {
            ThemeController.isDark = true
        }
    }

    /**
     * Loads the first sample in the gallery: the card's button, then the confirmation the gallery
     * shows before it replaces the editor's contents.
     *
     * Both buttons read "Load Sample", so the dialog's is taken as the last match — the dialog is
     * composed after the grid.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.loadFirstSample() {
        onAllNodesWithText(text = LOAD_SAMPLE)[0].performClick()
        waitForIdle()
        onAllNodesWithText(text = LOAD_SAMPLE).onLast().performClick()
        // The sample is read through a suspending resource load, so the gallery is still on screen
        // the moment the click returns.
        waitUntil(timeoutMillis = LOAD_TIMEOUT_MS) {
            onAllNodesWithText(text = LOAD_SAMPLE).fetchSemanticsNodes().isEmpty()
        }
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.capture(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        ImageIO.write(image, "png", File(outputDir, "$name.png"))
    }

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
        const val LOAD_SAMPLE = "Load Sample"
        const val LOAD_TIMEOUT_MS = 15_000L
    }
}
