package ui.manifest

import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.manifest.model.ManifestPathKind
import ui.workbench.inspector.ManifestInspector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Choosing a manifest path from a dialog, without giving up typing it.
 *
 * Both halves are asserted, because the requirement has two: a `Choose…` button *and* a text box, and
 * the button must refuse — visibly, with the reason — while the project has never been saved. A dialog
 * hands back an absolute path, and a manifest path is relative to the manifest file, so before the first
 * save there is nothing for it to be relative to.
 */
class ManifestPathFieldTest {

    private val blockedReason =
        "Save the project first — a path in the manifest is relative to the manifest file."

    private fun stateWith(rulePath: String = "rules/main.rule") = ManifestEditorState(
        name = "rag",
        entries = listOf(
            EditableManifestEntry(
                id = "default",
                schemaPath = "schemas/schema.yaml",
                actionsPath = "schemas/actions.yaml",
                rulePaths = listOf(rulePath),
            ),
        ),
    )

    /** Renders the Inspector over a live state and returns what it held when the assertions finished. */
    @OptIn(ExperimentalTestApi::class)
    private fun inspect(
        choosePath: ((ManifestPathKind) -> String?)?,
        disabledReason: String?,
        rulePath: String = "rules/main.rule",
        assertions: androidx.compose.ui.test.DesktopComposeUiTest.() -> Unit,
    ): ManifestEditorState {
        lateinit var current: ManifestEditorState
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                var manifest by remember { mutableStateOf(value = stateWith(rulePath = rulePath)) }
                current = manifest
                AppTheme {
                    Surface {
                        ManifestInspector(
                            manifest = manifest,
                            onManifestChange = { edited -> manifest = edited },
                            activeEntryId = "default",
                            choosePath = choosePath,
                            choosePathDisabledReason = disabledReason,
                        )
                    }
                }
            }
            assertions()
            waitForIdle()
        }
        return current
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an unsaved project shows the reason the dialog cannot be used`() {
        inspect(choosePath = { "rules/chosen.rule" }, disabledReason = blockedReason) {
            // Under the field, not only on hover: hover text is invisible to anyone who does not hover.
            onAllNodesWithText(text = blockedReason, substring = true)[0].assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `clicking the disabled button changes nothing`() {
        val after = inspect(choosePath = { "rules/chosen.rule" }, disabledReason = blockedReason) {
            onAllNodesWithText(text = "Choose…")[0].performClick()
            waitForIdle()
        }

        assertEquals(
            expected = "schemas/schema.yaml",
            actual = after.entries.single().schemaPath,
            message = "the picker ran although it was blocked",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `choosing a schema file writes the path it returns`() {
        val after = inspect(choosePath = { kind -> "schemas/chosen-$kind.yaml" }, disabledReason = null) {
            onAllNodesWithText(text = "Choose…")[0].performClick()
            waitForIdle()
        }

        assertEquals(expected = "schemas/chosen-SCHEMA.yaml", actual = after.entries.single().schemaPath)
    }

    /** The button is a convenience beside the box, never a replacement for it. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a path can still be typed`() {
        val after = inspect(choosePath = { "rules/chosen.rule" }, disabledReason = null) {
            onNodeWithText(text = "schemas/schema.yaml").performTextReplacement(text = "other/schema.yaml")
            waitForIdle()
        }

        assertEquals(expected = "other/schema.yaml", actual = after.entries.single().schemaPath)
    }

    /**
     * The gap this closed: a rule file's row had no editor and nothing to offer as an option, so a wrong
     * path could only be removed and re-added.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a rule file path can be typed on its row`() {
        val after = inspect(choosePath = null, disabledReason = null, rulePath = "rules/typo.rule") {
            onNodeWithText(text = "rules/typo.rule").performTextReplacement(text = "rules/fixed.rule")
            waitForIdle()
        }

        assertEquals(expected = listOf("rules/fixed.rule"), actual = after.entries.single().rulePaths)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `no picker at all leaves the buttons out`() {
        inspect(choosePath = null, disabledReason = null) {
            assertTrue(actual = onAllNodesWithText(text = "Choose…").fetchSemanticsNodes().isEmpty())
        }
    }

    private companion object {
        const val WIDTH = 560
        const val HEIGHT = 900
    }
}
