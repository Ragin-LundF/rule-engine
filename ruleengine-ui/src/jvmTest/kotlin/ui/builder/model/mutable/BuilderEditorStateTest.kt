package ui.builder.model.mutable

import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    // ── groupConditions tests ──────────────────────────────────────────────

    @Test
    fun `groupConditions wraps specified nodes into a Group`() {
        val state = builderStateWithThreeConditions()

        state.groupConditions(ids = setOf("c1", "c2"))

        assertEquals(expected = 2, actual = state.conditionNodes.size)
        val group = state.conditionNodes[0]
        assertIs<MutableConditionNode.Group>(group)
        assertEquals(expected = 2, actual = group.nodes.size)
        assertEquals(expected = "c1", actual = group.nodes[0].id)
        assertEquals(expected = "c2", actual = group.nodes[1].id)

        val remaining = state.conditionNodes[1]
        assertIs<MutableConditionNode.Leaf>(remaining)
        assertEquals(expected = "c3", actual = remaining.id)
    }

    @Test
    fun `groupConditions with single id does nothing`() {
        val state = builderStateWithThreeConditions()
        state.groupConditions(ids = setOf("c1"))
        assertEquals(expected = 3, actual = state.conditionNodes.size)
        assertIs<MutableConditionNode.Leaf>(state.conditionNodes[0])
    }

    @Test
    fun `groupConditions preserves joinToPrevious on the group`() {
        val state = builderStateWithThreeConditions()
        state.groupConditions(ids = setOf("c1", "c2"))
        val group = state.conditionNodes[0] as MutableConditionNode.Group
        assertEquals(expected = "", actual = group.joinToPrevious)
        assertEquals(expected = "", actual = group.nodes[0].joinToPrevious)
        assertEquals(expected = "and", actual = group.nodes[1].joinToPrevious)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun builderStateWithThreeConditions(): BuilderEditorState {
        return BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditionNodes = listOf(
                    BuilderConditionNode.Condition(
                        nodeId = "c1",
                        field = "a",
                        operator = "equals",
                        value = "1",
                    ),
                    BuilderConditionNode.Condition(
                        nodeId = "c2",
                        field = "b",
                        operator = "equals",
                        value = "2",
                        joinToPrevious = "and",
                    ),
                    BuilderConditionNode.Condition(
                        nodeId = "c3",
                        field = "c",
                        operator = ">",
                        value = "3",
                        joinToPrevious = "or",
                    ),
                ),
                actions = emptyList(),
            )
        )
    }

    // ── ungroup tests ──────────────────────────────────────────────────────

    @Test
    fun `ungroup flattens a Group back to top-level nodes`() {
        val state = builderStateWithGroup()

        assertEquals(expected = 1, actual = state.conditionNodes.size)
        assertIs<MutableConditionNode.Group>(state.conditionNodes[0])

        state.ungroup(id = "grp-test")

        assertEquals(expected = 2, actual = state.conditionNodes.size)
        assertIs<MutableConditionNode.Leaf>(state.conditionNodes[0])
        assertIs<MutableConditionNode.Leaf>(state.conditionNodes[1])
        assertEquals(expected = "c1", actual = state.conditionNodes[0].id)
        assertEquals(expected = "c2", actual = state.conditionNodes[1].id)
    }

    @Test
    fun `ungroup preserves joins on promoted children`() {
        val state = builderStateWithGroup()
        state.ungroup(id = "grp-test")
        assertEquals(expected = "", actual = state.conditionNodes[0].joinToPrevious)
        assertEquals(expected = "and", actual = state.conditionNodes[1].joinToPrevious)
    }

    // ── addConditionInside tests ───────────────────────────────────────────

    @Test
    fun `addConditionInside appends condition into the target group`() {
        val state = builderStateWithGroup()
        state.addConditionInside(groupId = "grp-test", defaultField = "c", defaultOperator = "equals")

        val group = state.conditionNodes[0] as MutableConditionNode.Group
        assertEquals(expected = 3, actual = group.nodes.size)
        assertEquals(expected = "c", actual = (group.nodes[2] as MutableConditionNode.Leaf).inner.field)
        assertEquals(expected = "and", actual = group.nodes[2].joinToPrevious)
    }

    @Test
    fun `addConditionInside does nothing for nonexistent group`() {
        val state = builderStateWithGroup()
        state.addConditionInside(groupId = "nonexistent", defaultField = "x", defaultOperator = "equals")

        val group = state.conditionNodes[0] as MutableConditionNode.Group
        assertEquals(expected = 2, actual = group.nodes.size)
    }

    private fun builderStateWithGroup(): BuilderEditorState {
        return BuilderEditorState.fromBuilderRule(
            BuilderRule.Supported(
                id = "r1",
                conditionNodes = listOf(
                    BuilderConditionNode.Group(
                        nodeId = "grp-test",
                        nodes = listOf(
                            BuilderConditionNode.Condition(
                                nodeId = "c1",
                                field = "a",
                                operator = "equals",
                                value = "1",
                            ),
                            BuilderConditionNode.Condition(
                                nodeId = "c2",
                                field = "b",
                                operator = "equals",
                                value = "2",
                                joinToPrevious = "and",
                            ),
                        ),
                    ),
                ),
                actions = emptyList(),
            )
        )
    }
}
