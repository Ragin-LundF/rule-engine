package ui.workbench.workspace

import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.parser.Parser
import ui.builder.RuleAstToBuilderMapper
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.selection.SelectionStep
import ui.workbench.model.InspectorItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The context bar's trail and the `Esc` walk are the same path read in two directions, so they are
 * tested together: every crumb must select what its label names, and going up one level must land on
 * the crumb before the last.
 */
class WorkspaceCrumbsTest {

    private val state: BuilderEditorState = stateOf(
        dsl = """
            rule "refer" {
              when
                ${'$'}ratio > 0.35
                and (sum(loans.balance) > 100
                or count(loans) >= 2)
              then
                decision "refer"
                set reason = "debt"
            }
        """.trimIndent(),
    )

    private val group = state.conditionNodes[1] as MutableConditionNode.Group
    private val nested = group.nodes[0]

    @Test
    fun `a nested operand is named from the file down to the side being edited`() {
        val item = InspectorItem.Condition(conditionId = nested.id, steps = listOf(SelectionStep.Left))

        val crumbs = WorkspaceCrumbs.of(fileName = "underwriting.rule", ruleOrdinal = 3, state = state, item = item)

        assertEquals(
            expected = listOf(
                "underwriting.rule",
                "Rule 3 · refer",
                "Conditions",
                "Group 2",
                "Condition 2.1",
                "Left value",
            ),
            actual = crumbs.map { crumb -> crumb.label },
        )
        // The file is orientation only; every other part is a selection.
        assertNull(actual = crumbs.first().target)
        assertEquals(expected = InspectorItem.Condition(conditionId = group.id), actual = crumbs[3].target)
        assertEquals(expected = item.copy(steps = emptyList()), actual = crumbs[4].target)
        assertEquals(expected = item, actual = crumbs.last().target)
    }

    @Test
    fun `escape walks out of the operand, then into the group, then to the rule`() {
        val operand = InspectorItem.Condition(conditionId = nested.id, steps = listOf(SelectionStep.Left))

        val row = WorkspaceCrumbs.upOneLevel(state = state, item = operand)
        val groupItem = WorkspaceCrumbs.upOneLevel(state = state, item = row)
        val rule = WorkspaceCrumbs.upOneLevel(state = state, item = groupItem)

        assertEquals(expected = InspectorItem.Condition(conditionId = nested.id), actual = row)
        assertEquals(expected = InspectorItem.Condition(conditionId = group.id), actual = groupItem)
        assertEquals(expected = InspectorItem.Rule(id = "refer"), actual = rule)
        assertNull(actual = WorkspaceCrumbs.upOneLevel(state = state, item = rule))
    }

    @Test
    fun `a statement is named by its lane in plain words`() {
        val action = state.actionsOf(branch = RuleBranch.THEN).single()
        val item = InspectorItem.Statement(branch = RuleBranch.THEN, statementId = action.id)

        val crumbs = WorkspaceCrumbs.of(fileName = null, ruleOrdinal = null, state = state, item = item)

        assertEquals(
            expected = listOf("refer", "When true", "Action decision"),
            actual = crumbs.map { crumb -> crumb.label },
        )
        assertEquals(
            expected = "action decision",
            actual = WorkspaceCrumbs.editingLabel(state = state, item = item),
        )
    }

    @Test
    fun `a selection that was edited away stops the trail at the rule`() {
        val item = InspectorItem.Condition(conditionId = "gone")

        val crumbs = WorkspaceCrumbs.of(fileName = "f.rule", ruleOrdinal = 1, state = state, item = item)

        assertEquals(expected = listOf("f.rule", "Rule 1 · refer"), actual = crumbs.map { crumb -> crumb.label })
        assertNull(actual = WorkspaceCrumbs.editingLabel(state = state, item = item))
    }

    @Test
    fun `the editing label names the deepest level`() {
        val item = InspectorItem.Condition(
            conditionId = nested.id,
            steps = listOf(SelectionStep.Left, SelectionStep.Segment(index = 0)),
        )

        assertEquals(expected = "path step 1", actual = WorkspaceCrumbs.editingLabel(state = state, item = item))
        val row = InspectorItem.Condition(conditionId = nested.id)
        assertEquals(expected = "condition 2.1", actual = WorkspaceCrumbs.editingLabel(state = state, item = row))
    }

    private fun stateOf(dsl: String): BuilderEditorState {
        val rule = RuleAstToBuilderMapper.map(rule = Parser(input = dsl).parseRules().single())
        return BuilderEditorState.fromBuilderRule(rule = rule)
    }
}
