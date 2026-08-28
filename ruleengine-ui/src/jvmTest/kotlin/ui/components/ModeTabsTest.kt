package ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.Bg
import ui.workbench.model.mode.SchemaMode
import ui.workbench.model.mode.displayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one mode switch, and the three things the areas need from it.
 *
 * Driven with [SchemaMode] rather than a fixture enum: the strip and the display names have to work
 * together, and a private test enum would prove only that the composable compiles.
 */
class ModeTabsTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a tab shows its icon beside its label`() {
        runTabs(icon = ::iconOf) {
            assertPresent(text = "⊞ ${SchemaMode.VISUAL.displayName}")
            assertPresent(text = "{ } ${SchemaMode.YAML.displayName}")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `dropping the labels leaves the icons alone on the tab`() {
        runTabs(icon = ::iconOf, showLabels = false) {
            assertPresent(text = "⊞")
            assertPresent(text = "{ }")

            val labelled = onAllNodesWithText(text = "⊞ ${SchemaMode.VISUAL.displayName}")
            assertTrue(
                actual = labelled.fetchSemanticsNodes().isEmpty(),
                message = "the label should be gone, not merely narrower",
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a strip with no icons keeps its labels however narrow it is told to be`() {
        // Otherwise the caller that asks for icon-only tabs on a strip that has none gets a row of
        // blank, unlabelled click targets.
        runTabs(icon = null, showLabels = false) {
            assertPresent(text = SchemaMode.VISUAL.displayName)
            assertPresent(text = SchemaMode.YAML.displayName)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `clicking a tab reports that mode`() {
        var selected: SchemaMode? = null

        runTabs(icon = ::iconOf, onSelect = { mode -> selected = mode }) {
            onNodeWithText(text = "{ } ${SchemaMode.YAML.displayName}").performClick()
            waitForIdle()
        }

        assertEquals(expected = SchemaMode.YAML, actual = selected)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a subordinate strip is still a working switch`() {
        var selected: SchemaMode? = null

        runTabs(icon = null, subordinate = true, onSelect = { mode -> selected = mode }) {
            onNodeWithText(text = SchemaMode.YAML.displayName).performClick()
            waitForIdle()
        }

        assertEquals(expected = SchemaMode.YAML, actual = selected)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertPresent(text: String) {
        assertTrue(
            actual = onAllNodesWithText(text = text).fetchSemanticsNodes().isNotEmpty(),
            message = "expected \"$text\" on a tab",
        )
    }

    private fun iconOf(mode: SchemaMode): String {
        return when (mode) {
            SchemaMode.VISUAL -> "⊞"
            SchemaMode.YAML -> "{ }"
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runTabs(
        icon: ((SchemaMode) -> String)?,
        showLabels: Boolean = true,
        subordinate: Boolean = false,
        onSelect: (SchemaMode) -> Unit = {},
        assertions: DesktopComposeUiTest.() -> Unit,
    ) {
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        var current by remember { mutableStateOf(value = SchemaMode.VISUAL) }
                        ModeTabs(
                            modes = SchemaMode.entries,
                            current = current,
                            label = { mode -> mode.displayName },
                            onSelect = { mode ->
                                current = mode
                                onSelect(mode)
                            },
                            icon = icon,
                            showLabels = showLabels,
                            subordinate = subordinate,
                        )
                    }
                }
            }
            assertions()
        }
    }

    private companion object {
        const val WIDTH = 600
        const val HEIGHT = 120
    }
}
