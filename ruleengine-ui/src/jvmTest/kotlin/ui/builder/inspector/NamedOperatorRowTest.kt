package ui.builder.inspector

import androidx.compose.material.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import ui.AppTheme
import ui.builder.model.BuilderAction
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.CatalogFieldInfo
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import kotlin.test.Test

/**
 * A plain row with a named operator — `purpose contains "rent"` from the Financial Transactions sample.
 *
 * The row is valid as written. Its sides cannot become computed while the operator is `contains`, and
 * the editor says why — but only when the author asks for a computed side. Shown up front, the reason
 * read as a validation error on a correct rule.
 */
@OptIn(ExperimentalTestApi::class)
class NamedOperatorRowTest {

    private val fields = BuilderCatalog.of(
        fields = listOf(
            CatalogFieldInfo(id = "purpose", type = "text"),
            CatalogFieldInfo(id = "amount", type = "decimal"),
        ),
    )

    @Test
    fun `a contains row shows no warning until a computed side is asked for`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "rent-payment",
                conditionNodes = listOf(
                    BuilderConditionNode.Condition(
                        nodeId = "cond-1",
                        field = "purpose",
                        operator = "contains",
                        value = "rent",
                    ),
                ),
                actions = listOf(BuilderAction(id = "act-1", name = "label", arguments = listOf("\"rent\""))),
            ),
        )
        val condition = (state.conditionNodes.first() as MutableConditionNode.Leaf).inner

        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent {
                AppTheme {
                    Surface {
                        ConditionEditor(
                            condition = condition,
                            state = state,
                            fields = fields,
                            onEdited = {},
                            onSelect = {},
                            onMessage = {},
                        )
                    }
                }
            }

            onNodeWithText(text = REASON, substring = true).assertDoesNotExist()

            onAllNodesWithText(text = "Calculation").onFirst().performClick()

            onNodeWithText(text = REASON, substring = true).assertIsDisplayed()
        }
    }

    private companion object {
        const val REASON = "only exists as a named operator"
        const val WIDTH = 520
        const val HEIGHT = 900
    }
}
