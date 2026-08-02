package ui.manifest

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import kotlin.test.Test

/**
 * That the schema actually reaches the scope picker.
 *
 * [scopeIssue] is pinned on its own by `ScopeIssueTest`; what this adds is the wiring, which is the
 * half that was broken. The types travel from the parsed schema through five composables to get
 * here, and options computed correctly but never rendered are the same silent fallback as before.
 */
class ManifestScopeFeedbackTest {

    private val fieldTypes = mapOf("caseId" to "text", "reports" to "collection")

    private fun stateWith(scope: String) = ManifestEditorState(
        name = "rag",
        entries = listOf(
            EditableManifestEntry(
                id = "ragin-rules",
                schemaPath = "schemas/schema.yaml",
                actionsPath = "schemas/actions.yaml",
                rulePaths = listOf("rules/rule-2.rule"),
                scope = scope,
            ),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    private fun renderWith(scope: String, assertions: DesktopTest) = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                ManifestEditorPanel(
                    state = stateWith(scope = scope),
                    onStateChange = {},
                    activeEntryId = "ragin-rules",
                    onSelectEntry = {},
                    onAddEntry = {},
                    onRemoveEntry = {},
                    fromYaml = { stateWith(scope = scope) },
                    toYaml = { "" },
                    fieldTypes = fieldTypes,
                )
            }
        }
        assertions()
    }

    /** The exact case that shipped: a scope naming nothing, which the engine refuses to load. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a scope naming no field is reported under the field`() {
        renderWith(scope = "tag") {
            onNodeWithText(text = "scope 'tag' is not a field of the schema").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the same verdict reaches the Checks tab`() {
        renderWith(scope = "tag") {
            onNodeWithText(text = "Checks").performClick()
            waitForIdle()
            // Substring: the panel prefixes each issue with a bullet.
            onNodeWithText(
                text = "ragin-rules: scope 'tag' is not a field of the schema",
                substring = true,
            ).assertIsDisplayed()
        }
    }

    /** A dropdown cannot offer emptiness, so the default has to be a visible entry of its own. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `no scope shows the none entry`() {
        renderWith(scope = "") {
            onNodeWithText(text = SCOPE_NONE).assertIsDisplayed()
        }
    }

    /** What the picker exists for: the legal values are read off the schema, not typed from memory. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the menu offers the schema's collections and nothing else`() {
        renderWith(scope = "") {
            onNodeWithText(text = SCOPE_NONE).performClick()
            waitForIdle()
            onNodeWithText(text = "reports").assertIsDisplayed()
            // `caseId` is text, so it could never be scoped over.
            onAllNodesWithText(text = "caseId").assertCountEquals(expectedSize = 0)
        }
    }

    /**
     * A manifest can arrive from disk or from the YAML tab carrying a scope the picker would never
     * have offered. Swapping it for something legal would be a silent edit of the user's file, so
     * the widget keeps it and marks it instead.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an undeclared scope is kept and marked rather than dropped`() {
        renderWith(scope = "tag") {
            onNodeWithText(text = "tag ⚠").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a valid scope is left alone`() {
        renderWith(scope = "reports") {
            onNodeWithText(text = "Checks").performClick()
            waitForIdle()
            onNodeWithText(text = "✓ Manifest structure looks valid").assertIsDisplayed()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private typealias DesktopTest = androidx.compose.ui.test.DesktopComposeUiTest.() -> Unit
