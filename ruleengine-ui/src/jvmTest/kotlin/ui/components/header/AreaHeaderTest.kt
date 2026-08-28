package ui.components.header

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.Bg
import ui.components.ModeTabs
import ui.components.header.model.ActionEmphasis
import ui.components.header.model.BarDensity
import ui.components.header.model.BindingMenuItem
import ui.components.header.model.BindingSpec
import ui.components.header.model.HeaderAction
import ui.workbench.model.mode.SchemaMode
import ui.workbench.model.mode.displayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The header the four areas will share, and the three things it must never do:
 * lose the primary verb, clip an action, or hide something that has nowhere else to be reached.
 */
class AreaHeaderTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a wide header shows every label it has`() {
        runHeader(width = WIDE) {
            assertPresent(text = "Schema")
            assertPresent(text = "12 fields")
            assertPresent(text = "schema.yaml")
            assertPresent(text = "⊞ ${SchemaMode.VISUAL.displayName}")
            assertPresent(text = "⧉ Copy")
            assertPresent(text = "Rename")
            assertPresent(text = "✓ Validate")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the count is the first thing the bar gives up`() {
        // Before the file path truncates, and long before a tab or an action does: it is the only
        // decoration in the header.
        runHeader(width = TIGHT) {
            assertAbsent(text = "12 fields", where = "a bar with a path to fit in")
            assertPresent(text = "Schema")
            assertPresent(text = "⊞ ${SchemaMode.VISUAL.displayName}")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a rare action is only ever in the overflow menu`() {
        runHeader(width = WIDE) {
            assertAbsent(text = "Export overview…", where = "the bar")

            onNodeWithContentDescription(label = "More actions").performClick()
            waitForIdle()

            assertPresent(text = "Export overview…")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a narrow header drops the secondary labels but never the primary one`() {
        runHeader(width = NARROW) {
            assertAbsent(text = "⧉ Copy", where = "a compact bar")
            assertPresent(text = "⧉")
            // An action with no glyph cannot shrink, so it leaves the bar for the menu instead.
            assertAbsent(text = "Rename", where = "a compact bar")

            // The label is gone from the screen, not from the button: a glyph with no name is
            // unreachable by anything but a mouse.
            assertTrue(
                actual = onAllNodesWithContentDescription(label = "Copy").fetchSemanticsNodes().isNotEmpty(),
                message = "the collapsed action lost its accessible name",
            )
            assertPresent(text = "✓ Validate")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the tabs give up their labels only at the narrowest width`() {
        runHeader(width = NARROW) {
            assertPresent(text = "⊞ ${SchemaMode.VISUAL.displayName}")
        }
        runHeader(width = MINIMAL) {
            assertAbsent(text = "⊞ ${SchemaMode.VISUAL.displayName}", where = "a minimal bar")
            assertPresent(text = "⊞")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a sub-switch is drawn beside the tabs and told how much room there is`() {
        val densities = mutableListOf<BarDensity>()

        runHeader(width = WIDE, subTabs = { density ->
            densities.add(density)
            Text(text = "Outline")
        }) {
            assertPresent(text = "Outline")
        }

        assertEquals(expected = BarDensity.FULL, actual = densities.last())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `clicking an action reports it once`() {
        val fired = mutableListOf<String>()

        runHeader(width = WIDE, onAction = { id -> fired.add(id) }) {
            onNodeWithText(text = "✓ Validate").performClick()
            waitForIdle()
        }

        assertEquals(expected = listOf("validate"), actual = fired)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `choosing from the binding menu reports that item`() {
        var chosen: String? = null

        runHeader(width = WIDE, onBindingItem = { id -> chosen = id }) {
            onNodeWithText(text = "schema.yaml").performClick()
            waitForIdle()
            onNodeWithText(text = "Change…").performClick()
            waitForIdle()
        }

        assertEquals(expected = "change", actual = chosen)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertPresent(text: String) {
        assertTrue(
            actual = onAllNodesWithText(text = text).fetchSemanticsNodes().isNotEmpty(),
            message = "expected \"$text\" in the header",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun DesktopComposeUiTest.assertAbsent(text: String, where: String) {
        assertTrue(
            actual = onAllNodesWithText(text = text).fetchSemanticsNodes().isEmpty(),
            message = "did not expect \"$text\" in $where",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    private fun runHeader(
        width: Int,
        onAction: (String) -> Unit = {},
        onBindingItem: (String) -> Unit = {},
        subTabs: (@Composable (BarDensity) -> Unit)? = null,
        assertions: DesktopComposeUiTest.() -> Unit,
    ) {
        runDesktopComposeUiTest(width = width, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                        AreaHeader(
                            title = "Schema",
                            meta = "12 fields",
                            binding = BindingSpec(
                                label = "File",
                                value = "schema.yaml",
                                items = listOf(
                                    BindingMenuItem(id = "change", label = "Change…"),
                                    BindingMenuItem(id = "unlink", label = "Unlink"),
                                ),
                            ),
                            onBindingItem = onBindingItem,
                            tabs = { density ->
                                ModeTabs(
                                    modes = SchemaMode.entries,
                                    current = SchemaMode.VISUAL,
                                    label = { mode -> mode.displayName },
                                    onSelect = {},
                                    icon = { mode -> if (mode == SchemaMode.VISUAL) "⊞" else "{ }" },
                                    showLabels = density != BarDensity.MINIMAL,
                                )
                            },
                            subTabs = subTabs,
                            actions = ACTIONS,
                            onAction = onAction,
                        )
                    }
                }
            }
            assertions()
        }
    }

    private companion object {
        const val WIDE = 1_440
        const val TIGHT = 1_100
        const val NARROW = 700
        const val MINIMAL = 500
        const val HEIGHT = 200

        val ACTIONS = listOf(
            HeaderAction(id = "copy", label = "Copy", icon = "⧉"),
            HeaderAction(id = "rename", label = "Rename"),
            HeaderAction(
                id = "export",
                label = "Export overview…",
                emphasis = ActionEmphasis.OVERFLOW,
            ),
            HeaderAction(
                id = "validate",
                label = "Validate",
                icon = "✓",
                emphasis = ActionEmphasis.PRIMARY,
            ),
        )
    }
}
