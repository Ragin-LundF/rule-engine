package ui.builder.inspector

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
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogFieldInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two parts of a rule that hide inside something else: a `where` filter inside a path, and an
 * `extract` clause inside an action.
 *
 * Both are places where a value can be silently degraded. A filter's sides are full operands, so an
 * aggregate on the left has to survive as an aggregate rather than being flattened to a field name —
 * which is exactly what the old String-valued filter did. An extraction's pattern is written by hand,
 * so the box has to report every keystroke back to the action it belongs to.
 *
 * This replaces `components/row/FilterAndExtractionRowTest`, which drove the same behaviour through
 * `FilterConditionRow` and `ActionRowEditor`. Those rows are gone: filters are edited in the Inspector
 * (drilled into from a path step) and an extraction by [ExtractionEditor]. The two membership cases
 * that file also covered are gone with the heuristic behind them — see the note at the foot of this
 * file, which is the part worth reading.
 */
class FilterAndExtractionTest {

    private val textFields = BuilderCatalog.of(
        fields = listOf(
            CatalogFieldInfo(id = "iban", type = "text"),
            CatalogFieldInfo(id = "total", type = "decimal"),
        ),
    )

    // ── filters ───────────────────────────────────────────────────────────────

    @Test
    fun `a filter whose left side is an aggregate keeps that aggregate in the generated text`() {
        val filter = BuilderFilter(
            left = BuilderOperand.Aggregate(
                function = "count",
                path = listOf(BuilderPathStep(name = "items")),
            ),
            operator = ">",
            right = BuilderOperand.Literal(text = "2", numeric = true),
        )

        assertEquals(
            expected = "count(items) > 2",
            actual = OperandText.filterToDsl(filter = filter),
            message = "the aggregate must reach the text as a call, not as the bare path it reduces",
        )
    }

    @Test
    fun `a membership filter against a written-out list keeps its brackets`() {
        val filter = BuilderFilter(
            left = BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "status"))),
            operator = "in",
            right = BuilderOperand.ListLiteral(items = listOf("paid", "sent", "refunded")),
        )

        assertEquals(
            expected = """status in ["paid", "sent", "refunded"]""",
            actual = OperandText.filterToDsl(filter = filter),
        )
    }

    @Test
    fun `a membership filter against another field names it unquoted`() {
        val filter = BuilderFilter(
            left = BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "status"))),
            operator = "in",
            right = BuilderOperand.FieldRef(path = listOf(BuilderPathStep(name = "allowedStatuses"))),
        )

        // A named source has to stay a path, or it is quoted into a text literal that can never match.
        assertEquals(
            expected = "status in allowedStatuses",
            actual = OperandText.filterToDsl(filter = filter),
        )
    }

    // ── extraction ────────────────────────────────────────────────────────────

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `an extraction shows the clause it will generate`() {
        val extraction = BuilderExtraction(sourceField = "iban", pattern = "DE([0-9]+)", groupIndex = 1)

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface {
                        ExtractionEditor(
                            extraction = extraction,
                            fields = textFields,
                            write = {},
                            onEdited = {},
                        )
                    }
                }
            }

            onNodeWithText(text = """extract iban regex("DE([0-9]+)", 1)""").assertIsDisplayed()
            onNodeWithText(text = "DE([0-9]+)").assertIsDisplayed()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `editing the pattern reports the whole extraction back`() {
        val edited = editExtraction(shownAs = "DE([0-9]+)", typed = "AT([0-9]+)")

        assertEquals(
            expected = BuilderExtraction(sourceField = "iban", pattern = "AT([0-9]+)", groupIndex = 1),
            actual = edited,
            message = "the source field and capture group must survive an edit to the pattern alone",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `a half-typed capture group falls back to the whole match rather than to nothing`() {
        val edited = editExtraction(shownAs = "1", typed = "")

        // 0 is the one index that is always valid, so a cleared box cannot generate a rule that fails
        // to compile.
        assertEquals(expected = 0, actual = edited.groupIndex)
        assertEquals(
            expected = "DE([0-9]+)",
            actual = edited.pattern,
            message = "clearing the group box must not disturb the pattern beside it",
        )
    }

    /**
     * Replaces the text of the box currently showing [shownAs] with [typed] and returns the extraction
     * the editor reports back.
     *
     * [ExtractionEditor] is a stateless controlled component: it renders the value it is handed and
     * reports edits upward, exactly as the Inspector's operand editors do. So the value has to be fed
     * back in for the box to show what was typed. Holding the extraction in a plain variable instead
     * leaves every box rendering its original text, and each keystroke then overwrites the same first
     * character — which is what made the first version of these two tests fail.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun editExtraction(shownAs: String, typed: String): BuilderExtraction {
        lateinit var current: BuilderExtraction

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                var extraction by remember {
                    mutableStateOf(
                        value = BuilderExtraction(
                            sourceField = "iban",
                            pattern = "DE([0-9]+)",
                            groupIndex = 1,
                        ),
                    )
                }
                current = extraction

                AppTheme {
                    Surface {
                        ExtractionEditor(
                            extraction = extraction,
                            fields = textFields,
                            write = { value -> extraction = value },
                            onEdited = {},
                        )
                    }
                }
            }

            onNodeWithText(text = shownAs).performTextReplacement(text = typed)
            waitForIdle()
        }

        return current
    }

    private companion object {
        const val WIDTH = 520
        const val HEIGHT = 640
    }
}

/*
 * On the two dropped cases.
 *
 * The old file asserted that typing into an `in` filter's value box read `"paid, sent, refunded"` back
 * as a three-item list and a bare `allowedStatuses` back as a field reference. Both went through
 * `membershipOperand`, which guessed the kind from the shape of the text.
 *
 * That guess had no correct answer for a one-item list: `["paid"]` and the field `paid` are spelled the
 * same, so the heuristic had to pick one, and it picked the field — making a one-item list impossible
 * to type. The Inspector asks instead. A list is edited one value per row (so a comma inside a value is
 * no longer ambiguous either), and membership against a field is the side's Field kind. The kind is now
 * stated rather than inferred, which is why there is nothing left to test here: a ListLiteral editor
 * cannot produce anything but a ListLiteral. The two assertions above cover what the DSL does with each
 * once chosen.
 */
