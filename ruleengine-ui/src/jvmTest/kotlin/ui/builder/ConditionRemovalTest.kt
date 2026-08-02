package ui.builder

import ruleengine.dsl.parser.Parser
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Removing a condition row, including one inside a group.
 *
 * The × on a grouped row used to do nothing at all: removal only scanned the top level, so an id
 * belonging to a child of a `(a or b)` group matched nothing and the click was swallowed. Any rule
 * written with parentheses — most real ones — had rows that could not be deleted.
 */
class ConditionRemovalTest {

    private fun stateOf(condition: String): BuilderEditorState {
        val dsl = """
            rule "r" {
              description "d"
              when
                $condition
              then
                label "x"
            }
        """.trimIndent()
        val rule = Parser(input = dsl).parseRules().single()
        return BuilderEditorState.fromBuilderRule(rule = RuleAstToBuilderMapper.map(rule = rule))
    }

    private fun groupOf(state: BuilderEditorState): MutableConditionNode.Group =
        assertIs<MutableConditionNode.Group>(value = state.conditionNodes.first { it is MutableConditionNode.Group })

    @Test
    fun `a top-level row is removed`() {
        val state = stateOf(condition = "amount > 1\nand amount < 9")
        val target = state.conditionNodes.first().id

        state.removeCondition(id = target)

        assertEquals(expected = 1, actual = state.conditionNodes.size)
        assertTrue(actual = state.conditionNodes.none { it.id == target })
    }

    @Test
    fun `a row inside a group is removed`() {
        val state = stateOf(condition = "amount > 1\nand (amount < 5 or amount > 90)")
        val group = groupOf(state = state)
        val target = group.nodes.first().id

        state.removeCondition(id = target)

        assertEquals(expected = 1, actual = group.nodes.size)
        assertTrue(actual = group.nodes.none { it.id == target })
        // The group itself survives while it still has a child.
        assertEquals(expected = 2, actual = state.conditionNodes.size)
    }

    /** `()` does not parse, so a group that has lost its last child has to go with it. */
    @Test
    fun `a group whose last child is removed goes too`() {
        val state = stateOf(condition = "amount > 1\nand (amount < 5 or amount > 90)")
        val group = groupOf(state = state)

        group.nodes.map { node -> node.id }.forEach { id -> state.removeCondition(id = id) }

        assertEquals(expected = 1, actual = state.conditionNodes.size)
        assertTrue(actual = state.conditionNodes.none { it is MutableConditionNode.Group })
    }

    @Test
    fun `removing a group removes its children with it`() {
        val state = stateOf(condition = "amount > 1\nand (amount < 5 or amount > 90)")

        state.removeCondition(id = groupOf(state = state).id)

        assertEquals(expected = 1, actual = state.conditionNodes.size)
    }

    /** The generated DSL has to stay parseable after a nested removal. */
    @Test
    fun `the rule still generates valid DSL after a nested removal`() {
        val state = stateOf(condition = "amount > 1\nand (amount < 5 or amount > 90)")
        state.removeCondition(id = groupOf(state = state).nodes.first().id)

        val generated = BuilderToRuleDsl.generate(state = state).orEmpty()

        val reparsed = Parser(input = generated).parseRules().single()
        assertEquals(expected = "r", actual = reparsed.id)
    }

    @Test
    fun `an unknown id changes nothing`() {
        val state = stateOf(condition = "amount > 1\nand (amount < 5 or amount > 90)")

        state.removeCondition(id = "no-such-node")

        assertEquals(expected = 2, actual = state.conditionNodes.size)
        assertEquals(expected = 2, actual = groupOf(state = state).nodes.size)
    }
}
