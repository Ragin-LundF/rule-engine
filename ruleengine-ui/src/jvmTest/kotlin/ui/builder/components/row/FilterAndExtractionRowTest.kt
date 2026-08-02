package ui.builder.components.row

import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.builder.OperandText
import ui.builder.model.BuilderExtraction
import ui.builder.model.BuilderFilter
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathStep
import ui.builder.model.catalog.CatalogActionInfo
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.MutableBuilderAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renders the two rows this change rewrote, headlessly, through the same Compose harness the
 * screenshot tool uses.
 *
 * The round-trip tests prove the *model* carries a rich filter and an extraction. These prove the rows
 * built to edit them actually compose and report the right value back — an operand chip that throws on
 * an aggregate, or an extraction box wired to the wrong field, would pass every other test in the
 * suite.
 */
class FilterAndExtractionRowTest {

    private val elementCatalog = listOf(
        CatalogFieldInfo(id = "status", type = "text"),
        CatalogFieldInfo(id = "total", type = "decimal"),
        CatalogFieldInfo(
            id = "items",
            type = "collection",
            nestedFields = listOf(CatalogFieldInfo(id = "price", type = "decimal")),
        ),
    )

    private val flatOptions = listOf(
        CatalogFieldInfo(id = "status", type = "text"),
        CatalogFieldInfo(id = "total", type = "decimal"),
    )

    // ── filter rows ───────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a filter whose left side is an aggregate renders that aggregate`() {
        val aggregate = BuilderOperand.Aggregate(
            function = "count",
            path = listOf(BuilderPathStep(name = "items")),
        )
        val subject = BuilderFilter(
            left = aggregate,
            operator = ">",
            right = BuilderOperand.Literal(text = "2", numeric = true),
        )

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface {
                        FilterConditionRow(
                            filter = subject,
                            fieldOptions = flatOptions,
                            fields = elementCatalog,
                            onFilterChanged = {},
                            onRemove = {},
                        )
                    }
                }
            }

            // The chip label, i.e. proof the aggregate reached the row rather than being flattened to
            // a field name — which is exactly what the old String-valued filter did.
            onNodeWithText(text = OperandText.toLabel(operand = aggregate)).assertIsDisplayed()
            onNodeWithText(text = ">").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a membership filter shows its list as editable text and reads it back as a list`() {
        val updated = editMembership(
            initial = BuilderOperand.ListLiteral(items = listOf("paid", "sent")),
            shownAs = "paid, sent",
            typed = "paid, sent, refunded",
        )

        assertEquals(
            expected = BuilderOperand.ListLiteral(items = listOf("paid", "sent", "refunded")),
            actual = updated,
            message = "editing the box must keep the value a list rather than collapsing it to a literal",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a membership filter reads a bare name back as a field reference`() {
        val updated = editMembership(
            initial = BuilderOperand.ListLiteral(items = listOf("paid")),
            shownAs = "paid",
            typed = "allowedStatuses",
        )

        // A named source has to stay a path, or `OperandText` quotes it into a text literal that can
        // never match.
        assertEquals(
            expected = "allowedStatuses",
            actual = OperandText.toDsl(operand = updated),
            message = "a bare identifier is the name of a source, not a one-item list",
        )
    }

    /**
     * Types [typed] into the membership box of an `in` filter and returns the operand it reports back.
     *
     * The row is a stateless controlled component, so the caller has to feed the new value in again —
     * which is what `PathBreadcrumb` does with the updated path. Holding the filter constant instead
     * leaves the box showing its old text and every keystroke overwriting the same first character.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun editMembership(
        initial: BuilderOperand,
        shownAs: String,
        typed: String,
    ): BuilderOperand {
        lateinit var current: BuilderFilter

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                var filter by remember {
                    mutableStateOf(
                        value = BuilderFilter(
                            left = BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "status"))),
                            operator = "in",
                            right = initial,
                        )
                    )
                }
                current = filter

                AppTheme {
                    Surface {
                        FilterConditionRow(
                            filter = filter,
                            fieldOptions = flatOptions,
                            fields = elementCatalog,
                            onFilterChanged = { filter = it },
                            onRemove = {},
                        )
                    }
                }
            }

            onNodeWithText(text = shownAs).performTextReplacement(text = typed)
            waitForIdle()
        }

        return current.right
    }

    // ── extraction row ────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an action carrying an extraction renders its clause`() {
        val action = MutableBuilderAction(
            id = "act-1",
            name = "tag",
            arguments = listOf("$1"),
            extraction = BuilderExtraction(sourceField = "iban", pattern = "DE([0-9]+)", groupIndex = 1),
        )

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface {
                        ActionRowEditor(
                            action = action,
                            actions = listOf(CatalogActionInfo(name = "tag", argType = "string")),
                            fields = listOf(CatalogFieldInfo(id = "iban", type = "text")),
                            onChanged = {},
                            onRemove = {},
                        )
                    }
                }
            }

            onNodeWithText(text = "extract").assertIsDisplayed()
            onNodeWithText(text = "regex").assertIsDisplayed()
            onNodeWithText(text = "iban").assertIsDisplayed()
            onNodeWithText(text = "DE([0-9]+)").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `editing the pattern updates the action`() {
        val action = MutableBuilderAction(
            id = "act-1",
            name = "tag",
            arguments = listOf("$1"),
            extraction = BuilderExtraction(sourceField = "iban", pattern = "DE([0-9]+)", groupIndex = 1),
        )

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface {
                        ActionRowEditor(
                            action = action,
                            actions = listOf(CatalogActionInfo(name = "tag", argType = "string")),
                            fields = listOf(CatalogFieldInfo(id = "iban", type = "text")),
                            onChanged = {},
                            onRemove = {},
                        )
                    }
                }
            }

            onNodeWithText(text = "DE([0-9]+)").performTextReplacement(text = "AT([0-9]+)")
            waitForIdle()
        }

        assertEquals(
            expected = BuilderExtraction(sourceField = "iban", pattern = "AT([0-9]+)", groupIndex = 1),
            actual = action.extraction,
            message = "the pattern box must write back to the action, leaving the rest of the clause alone",
        )
    }

    private companion object {
        const val WIDTH = 900
        const val HEIGHT = 300
    }
}
