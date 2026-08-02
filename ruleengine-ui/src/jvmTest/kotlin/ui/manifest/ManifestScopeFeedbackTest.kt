package ui.manifest

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import kotlin.test.Test

/**
 * That the schema actually reaches the scope field.
 *
 * [scopeIssue] is pinned on its own by `ScopeIssueTest`; what this adds is the wiring, which is the
 * half that was broken. The types travel from the parsed schema through five composables to get
 * here, and a verdict computed correctly but never rendered is the same silent fallback as before.
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

    /** With nothing typed the field says what could be, since it is free text with no picker. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an empty scope lists the collections instead`() {
        renderWith(scope = "") {
            onNodeWithText(text = "Collections in this schema: reports").assertIsDisplayed()
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
