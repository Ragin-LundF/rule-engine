package ui.builder

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [BuilderEditorState] mutation helpers.
 */
class BuilderEditorStateTest {

    @Test
    fun `addCondition appends a condition and assigns a unique id`() {
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditionNodes = emptyList(),
                actions = emptyList(),
            )
        )

        val first = state.addCondition(defaultField = "amount", defaultOperator = ">=")
        val second = state.addCondition(defaultField = "purpose", defaultOperator = "equals")

        assertEquals(expected = 2, actual = state.conditionNodes.size)
        assertEquals(expected = "amount", actual = first.field)
        assertEquals(expected = "purpose", actual = second.field)
        assertEquals(expected = "and", actual = second.joinToPrevious)
    }

    @Test
    fun `removeCondition removes only the matching condition`() {
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditionNodes = listOf(
                    BuilderConditionNode.Condition(
                        nodeId = "c1",
                        field = "amount",
                        operator = ">=",
                        value = "100",
                    ),
                    BuilderConditionNode.Condition(
                        nodeId = "c2",
                        field = "purpose",
                        operator = "equals",
                        value = "rent",
                    ),
                ),
                actions = emptyList(),
            )
        )

        state.removeCondition(id = "c1")

        assertEquals(expected = 1, actual = state.conditionNodes.size)
        assertEquals(expected = "c2", actual = state.conditionNodes[0].id)
    }

    @Test
    fun `addAction appends an action and assigns a unique id`() {
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditionNodes = emptyList(),
                actions = emptyList(),
            )
        )

        state.addAction(defaultName = "label")

        assertEquals(expected = 1, actual = state.actions.size)
        assertEquals(expected = "label", actual = state.actions[0].name)
    }
}
