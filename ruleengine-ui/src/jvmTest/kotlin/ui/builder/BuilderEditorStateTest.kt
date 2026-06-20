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
                conditions = emptyList(),
                conditionJoin = ConditionJoin.SINGLE,
                actions = emptyList(),
            )
        )

        val first = state.addCondition(defaultField = "amount", defaultOperator = ">=")
        val second = state.addCondition(defaultField = "purpose", defaultOperator = "equals")

        assertEquals(2, state.conditions.size)
        assertEquals("amount", first.field)
        assertEquals("purpose", second.field)
    }

    @Test
    fun `removeCondition removes only the matching condition`() {
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditions = listOf(
                    BuilderCondition(id = "c1", field = "amount", operator = ">=", value = "100"),
                    BuilderCondition(id = "c2", field = "purpose", operator = "equals", value = "\"rent\""),
                ),
                conditionJoin = ConditionJoin.AND,
                actions = emptyList(),
            )
        )

        state.removeCondition(id = "c1")

        assertEquals(1, state.conditions.size)
        assertEquals("c2", state.conditions[0].id)
    }

    @Test
    fun `addAction appends an action and assigns a unique id`() {
        val state = BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditions = emptyList(),
                conditionJoin = ConditionJoin.SINGLE,
                actions = emptyList(),
            )
        )

        state.addAction(defaultName = "label")

        assertEquals(1, state.actions.size)
        assertEquals("label", state.actions[0].name)
    }
}
