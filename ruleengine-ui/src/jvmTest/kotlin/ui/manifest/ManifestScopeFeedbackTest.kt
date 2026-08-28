package ui.manifest

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.manifest.model.EditableManifestEntry
import ui.manifest.model.ManifestEditorState
import ui.schema.IssueLevel
import ui.workbench.inspector.ManifestInspector
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the schema actually reaches the scope picker.
 *
 * [scopeIssue] is pinned on its own by `ScopeIssueTest`; what this adds is the wiring, which is the half
 * that was broken. The types travel from the parsed schema through several composables to get here, and
 * options computed correctly but never rendered are the same silent fallback as before.
 *
 * The assertions are split because the control is: the **canvas** shows a scope and its verdict, and the
 * **Inspector** picks one. That division is the point of the rework — a canvas with no editing controls
 * can be read — so a test that drove both through one composable would be asserting the old design.
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

    /** The canvas: what the manifest currently says, and what is wrong with it. */
    @OptIn(ExperimentalTestApi::class)
    private fun renderCanvas(scope: String, assertions: DesktopTest) = runDesktopComposeUiTest {
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

    /** The Inspector: where a scope is chosen. */
    @OptIn(ExperimentalTestApi::class)
    private fun renderInspector(
        scope: String,
        types: Map<String, String>? = fieldTypes,
        assertions: DesktopTest,
    ) = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                ManifestInspector(
                    manifest = stateWith(scope = scope),
                    onManifestChange = {},
                    activeEntryId = "ragin-rules",
                    fieldTypes = types,
                )
            }
        }
        assertions()
    }

    // ── the canvas ────────────────────────────────────────────────────────────

    /** The exact case that shipped: a scope naming nothing, which the engine refuses to load. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a scope naming no field is reported on the row`() {
        renderCanvas(scope = "tag") {
            onNodeWithText(
                text = "scope 'tag' is not a field of the schema",
                substring = true,
            ).assertIsDisplayed()
        }
    }

    /**
     * The distinction the round trip through "collection names only" destroyed: a field that exists but
     * is the wrong type gets its own verdict, not the one for a name that exists nowhere.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a scope naming a non-collection says so, rather than saying it is unknown`() {
        renderCanvas(scope = "caseId") {
            onNodeWithText(
                text = "scope 'caseId' is text, not a collection",
                substring = true,
            ).assertIsDisplayed()
        }
    }

    /**
     * The same verdict, at the seam the dock's Checks tab reads.
     *
     * Asserted against [ManifestIssues] rather than through a rendered tab because the Checks tab is no
     * longer a mode of this panel — it is a tab of the dock, which lives a layer above and would have to
     * be stood up here to be clicked. What matters is that one function answers for both surfaces, so
     * the row and the check can never disagree.
     */
    @Test
    fun `the same verdict reaches the checks the dock shows`() {
        val issues = ManifestIssues.of(
            state = stateWith(scope = "tag"),
            activeEntryId = "ragin-rules",
            fieldTypes = fieldTypes,
        )

        assertTrue(
            actual = issues.any { issue ->
                issue.level == IssueLevel.ERROR &&
                    issue.path == "ragin-rules" &&
                    issue.message.contains(other = "scope 'tag' is not a field of the schema")
            },
            message = "the scope verdict never reached the checks: $issues",
        )
    }

    @Test
    fun `a valid scope produces no check of its own`() {
        val issues = ManifestIssues.of(
            state = stateWith(scope = "reports"),
            activeEntryId = "ragin-rules",
            fieldTypes = fieldTypes,
        )

        assertTrue(
            actual = issues.none { issue -> issue.message.contains(other = "scope") },
            message = "a valid scope was still reported: $issues",
        )
    }

    // ── the Inspector ─────────────────────────────────────────────────────────

    /** The picker cannot offer emptiness, so the default is a visible option of its own. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `no scope shows the none option`() {
        renderInspector(scope = "") {
            onNodeWithText(text = SCOPE_NONE).assertIsDisplayed()
        }
    }

    /** What the picker exists for: the legal values are read off the schema, not typed from memory. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the picker offers the schema's collections and nothing else`() {
        renderInspector(scope = "") {
            onNodeWithText(text = "reports").assertIsDisplayed()
            // `caseId` is text, so it could never be scoped over.
            onAllNodesWithText(text = "caseId").assertCountEquals(expectedSize = 0)
        }
    }

    /**
     * A manifest can arrive from disk or from the YAML tab carrying a scope the picker would never have
     * offered. Swapping it for something legal would be a silent edit of the user's file, so the picker
     * keeps it and marks it instead.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an undeclared scope is kept and marked rather than dropped`() {
        renderInspector(scope = "tag") {
            onNodeWithText(text = "tag ⚠").assertIsDisplayed()
        }
    }

    /**
     * With no schema there is nothing to check a name against, so the value is offered as itself rather
     * than accused of being undeclared.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `with no schema loaded the carried scope is offered unmarked`() {
        renderInspector(scope = "tag", types = null) {
            onNodeWithText(text = "tag").assertIsDisplayed()
            onAllNodesWithText(text = "tag ⚠").assertCountEquals(expectedSize = 0)
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private typealias DesktopTest = androidx.compose.ui.test.DesktopComposeUiTest.() -> Unit
