package ui.builder.board

import ruleengine.core.domain.dto.RuleBranch
import ruleengine.dsl.parser.Parser
import ui.builder.BuilderToRuleDsl
import ui.builder.RuleAstToBuilderMapper
import ui.builder.board.model.DropTarget
import ui.builder.board.ribbon.RibbonModel
import ui.builder.board.ribbon.VariableFlow
import ui.builder.model.BuilderRule
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.mutable.moveConditionInto
import ui.builder.model.mutable.moveStatement
import ui.workbench.model.catalog.CatalogRule
import ui.workbench.model.catalog.RuleTreeFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The board's two gestures, and the ribbon it draws them under.
 *
 * Everything here is asserted against the model and the generated DSL rather than against a rendered
 * board. A drag is a gesture over a `Rect` registry, which a unit test cannot usefully drive; what a
 * unit test *can* pin down is the part that matters — whether the drop was legal, and what it did to
 * the rule text. So the drop rules and the mutations are tested directly, at the seam the gesture calls.
 */
class BoardBehaviourTest {

    private fun stateOf(dsl: String): BuilderEditorState {
        val ast = Parser(input = dsl).parseRules().single()
        val rule = RuleAstToBuilderMapper.map(rule = ast)
        assertTrue(actual = rule is BuilderRule.Supported, message = "fixture not editable")
        return BuilderEditorState.fromBuilderRule(rule = rule)
    }

    private fun generate(state: BuilderEditorState): String {
        return assertNotNull(actual = BuilderToRuleDsl.generate(state = state))
    }

    private val threeRows = """
        rule "r" {
          when
            a == 1
            and b == 2
            and c == 3
          then
            flag "hit"
        }
    """.trimIndent()

    // ── dragging a row (task 3.7) ─────────────────────────────────────────────

    @Test
    fun `dropping one row on another groups the two`() {
        val state = stateOf(dsl = threeRows)
        val (first, second) = state.conditionNodes.take(n = 2).map { node -> node.id }

        val refusal = validateDrop(
            state = state,
            subject = BoardDragState.DragSubject.Row(nodeId = second),
            target = DropTarget.Row(nodeId = first),
        )
        assertNull(actual = refusal, message = "grouping two distinct rows must be allowed")

        applyRowDrop(state = state, nodeId = second, target = DropTarget.Row(nodeId = first))
        val generated = generate(state = state)

        assertTrue(actual = generated.contains("("), message = "no bracket in:\n$generated")
        assertTrue(actual = generated.contains("c == 3"), message = generated)
        Parser(input = generated).parseRules().single()
    }

    @Test
    fun `a row cannot be grouped with itself`() {
        val state = stateOf(dsl = threeRows)
        val id = state.conditionNodes.first().id

        assertNotNull(
            actual = validateDrop(
                state = state,
                subject = BoardDragState.DragSubject.Row(nodeId = id),
                target = DropTarget.Row(nodeId = id),
            ),
        )
    }

    @Test
    fun `dropping a row into a group moves it inside the brackets`() {
        val state = stateOf(dsl = threeRows)
        val grouped = state.conditionNodes.take(n = 2).map { node -> node.id }.toSet()
        state.groupConditions(ids = grouped)

        val group = state.conditionNodes.filterIsInstance<MutableConditionNode.Group>().single()
        val outsider = state.conditionNodes.first { node -> node.id != group.id }.id

        assertTrue(actual = state.moveConditionInto(id = outsider, groupId = group.id))

        assertEquals(
            expected = 3,
            actual = group.nodes.size,
            message = "the row should now be the group's third child",
        )
        assertEquals(
            expected = 1,
            actual = state.conditionNodes.size,
            message = "and should no longer be at the top level",
        )
        Parser(input = generate(state = state)).parseRules().single()
    }

    @Test
    fun `a group cannot be dropped inside itself`() {
        val state = stateOf(dsl = threeRows)
        state.groupConditions(ids = state.conditionNodes.take(n = 2).map { node -> node.id }.toSet())
        val outer = state.conditionNodes.filterIsInstance<MutableConditionNode.Group>().single()

        // Nest a second group inside the first, then try to drop the outer one into it. Allowing that
        // would detach the whole subtree from the rule.
        val inner = outer.nodes.first().id
        state.wrapInGroup(id = inner)
        val innerGroup = outer.nodes.filterIsInstance<MutableConditionNode.Group>().single()

        assertNotNull(
            actual = validateDrop(
                state = state,
                subject = BoardDragState.DragSubject.Row(nodeId = outer.id),
                target = DropTarget.Group(groupId = innerGroup.id),
            ),
            message = "dropping a group into its own descendant must be refused",
        )
    }

    @Test
    fun `a condition cannot be dropped into an outcome lane`() {
        val state = stateOf(dsl = threeRows)
        val id = state.conditionNodes.first().id

        assertNotNull(
            actual = validateDrop(
                state = state,
                subject = BoardDragState.DragSubject.Row(nodeId = id),
                target = DropTarget.Lane(branch = RuleBranch.THEN),
            ),
        )
    }

    // ── dragging a statement between lanes (task 3.8) ─────────────────────────

    @Test
    fun `moving the only then action is refused and the text is unchanged`() {
        val state = stateOf(dsl = threeRows)
        val before = generate(state = state)
        val onlyAction = state.actionsOf(branch = RuleBranch.THEN).single().id

        val refusal = validateDrop(
            state = state,
            subject = BoardDragState.DragSubject.Statement(
                statementId = onlyAction,
                from = DropTarget.Lane(branch = RuleBranch.THEN),
            ),
            target = DropTarget.Lane(branch = RuleBranch.NOT_EXISTS),
        )

        assertNotNull(actual = refusal, message = "emptying `then` must be refused with a reason")

        // And the mutation itself refuses too, not only the validator — a gesture is not the only way in.
        assertFalse(
            actual = state.moveStatement(
                id = onlyAction,
                from = RuleBranch.THEN,
                to = RuleBranch.NOT_EXISTS,
            ),
        )
        assertEquals(expected = before, actual = generate(state = state))
    }

    @Test
    fun `a second action can be moved to another lane`() {
        val state = stateOf(dsl = threeRows)
        state.addAction(defaultName = "label", defaultArgCount = 1, branch = RuleBranch.THEN)
        val moved = state.actionsOf(branch = RuleBranch.THEN).last().id

        assertTrue(
            actual = state.moveStatement(id = moved, from = RuleBranch.THEN, to = RuleBranch.ELSE),
        )

        assertEquals(expected = 1, actual = state.actionsOf(branch = RuleBranch.THEN).size)
        assertEquals(expected = 1, actual = state.actionsOf(branch = RuleBranch.ELSE).size)
        assertTrue(
            actual = generate(state = state).contains("else"),
            message = "the else block should now exist",
        )
    }

    @Test
    fun `an outcome cannot be dropped onto a condition`() {
        val state = stateOf(dsl = threeRows)
        val action = state.actionsOf(branch = RuleBranch.THEN).single().id
        val row = state.conditionNodes.first().id

        assertNotNull(
            actual = validateDrop(
                state = state,
                subject = BoardDragState.DragSubject.Statement(
                    statementId = action,
                    from = DropTarget.Lane(branch = RuleBranch.THEN),
                ),
                target = DropTarget.Row(nodeId = row),
            ),
        )
    }

    // ── the ribbon (tasks 3.2, 3.3, 3.4) ──────────────────────────────────────

    private fun ribbonFixture(): Pair<List<RuleTreeFile>, List<BuilderRule>> {
        val dsl = """
            rule "sets-it" {
              when
                amount >= 1
              then
                set budget = 100
            }
            rule "reads-it" {
              when
                ${'$'}budget <= amount
              then
                flag "over"
                stop
            }
            rule "reads-unset" {
              when
                ${'$'}nobodySets <= amount
              then
                flag "never"
            }
        """.trimIndent()

        val rules = Parser(input = dsl).parseRules().map { ast -> RuleAstToBuilderMapper.map(rule = ast) }
        val files = listOf(
            RuleTreeFile(
                relativePath = "rules.rules",
                rules = rules.map { rule ->
                    CatalogRule(id = (rule as BuilderRule.Supported).id)
                },
            ),
        )
        return files to rules
    }

    @Test
    fun `the ribbon numbers rules continuously in evaluation order`() {
        val (files, rules) = ribbonFixture()

        val groups = RibbonModel.groups(files = files, rules = rules)
        val cards = groups.single().cards

        assertEquals(expected = listOf(1, 2, 3), actual = cards.map { card -> card.ordinal })
        assertEquals(
            expected = listOf("sets-it", "reads-it", "reads-unset"),
            actual = cards.map { card -> card.ruleId },
        )
    }

    @Test
    fun `a card states what it reads and sets, including when it is nothing`() {
        val (files, rules) = ribbonFixture()
        val cards = RibbonModel.groups(files = files, rules = rules).single().cards

        assertEquals(expected = listOf("budget"), actual = cards[0].sets)
        assertEquals(expected = emptyList(), actual = cards[0].reads)
        assertEquals(expected = listOf("budget"), actual = cards[1].reads)
        assertEquals(expected = emptyList(), actual = cards[1].sets)
    }

    @Test
    fun `the halting rule is the one that says stop`() {
        val (files, rules) = ribbonFixture()
        val cards = RibbonModel.groups(files = files, rules = rules).single().cards

        assertEquals(
            expected = listOf("reads-it"),
            actual = cards.filter { card -> card.halts }.map { card -> card.ruleId },
        )
    }

    @Test
    fun `the group states its own width from its card count`() {
        val (files, rules) = ribbonFixture()

        // The property the renderer relies on to size the row by arithmetic rather than measurement —
        // the cross-engine overlap bug the prototype hit. If this ever stops matching, the ribbon's
        // cards start overlapping again.
        assertEquals(expected = 3, actual = RibbonModel.groups(files = files, rules = rules).single().cardCount)
    }

    @Test
    fun `a variable's flow names its producer and its readers`() {
        val (files, rules) = ribbonFixture()
        val groups = RibbonModel.groups(files = files, rules = rules)

        val flow = VariableFlow.of(variable = "budget", groups = groups)

        assertEquals(expected = listOf(1), actual = flow.producers)
        assertEquals(expected = listOf(2), actual = flow.readers)
        assertTrue(actual = flow.orphanReaders.isEmpty())
        assertTrue(actual = flow.touches(ordinal = 1) && flow.touches(ordinal = 2))
        assertFalse(actual = flow.touches(ordinal = 3))
    }

    @Test
    fun `a rule reading a variable nothing sets is reported as an orphan`() {
        val (files, rules) = ribbonFixture()
        val groups = RibbonModel.groups(files = files, rules = rules)

        val flow = VariableFlow.of(variable = "nobodySets", groups = groups)

        // The board's one genuine warning: this rule parses, validates, runs, and can never fire.
        assertTrue(actual = flow.producers.isEmpty())
        assertEquals(expected = listOf(3), actual = flow.orphanReaders)
        assertTrue(actual = flow.readers.isEmpty())

        val card = groups.single().cards[2]
        assertEquals(
            expected = listOf("nobodySets"),
            actual = VariableFlow.unresolvedReads(card = card, groups = groups),
        )
    }

    @Test
    fun `a reader earlier in the run than its producer does not see it`() {
        val dsl = """
            rule "reads-first" {
              when
                ${'$'}late <= amount
              then
                flag "a"
            }
            rule "sets-later" {
              when
                amount >= 1
              then
                set late = 5
            }
        """.trimIndent()
        val rules = Parser(input = dsl).parseRules().map { ast -> RuleAstToBuilderMapper.map(rule = ast) }
        val files = listOf(
            RuleTreeFile(
                relativePath = "r.rules",
                rules = rules.map { rule -> CatalogRule(id = (rule as BuilderRule.Supported).id) },
            ),
        )
        val groups = RibbonModel.groups(files = files, rules = rules)

        val flow = VariableFlow.of(variable = "late", groups = groups)

        // Only an *earlier* rule publishes. A highlight that joined these two would draw an arrow
        // backwards through the run and assert a connection the engine does not make.
        assertEquals(expected = listOf(2), actual = flow.producers)
        assertEquals(expected = listOf(1), actual = flow.orphanReaders)
        assertTrue(actual = flow.readers.isEmpty())
    }

    @Test
    fun `a rule the builder cannot render still gets a card`() {
        val files = listOf(
            RuleTreeFile(relativePath = "r.rules", rules = listOf(CatalogRule(id = "opaque"))),
        )
        val rules = listOf(BuilderRule.Unsupported(id = "opaque", reason = "unsupported syntax"))

        val card = RibbonModel.groups(files = files, rules = rules).single().cards.single()

        // Leaving it out would make the ribbon claim an evaluation order that skips a rule that runs.
        assertEquals(expected = "opaque", actual = card.ruleId)
        assertEquals(expected = 1, actual = card.ordinal)
        assertTrue(actual = card.locked)
    }

    @Test
    fun `a variable read deep inside an expression still counts as a read`() {
        val dsl = """
            rule "deep" {
              when
                abs(${'$'}budget - sum(invoices.amount)) > 10
              then
                flag "hit"
            }
        """.trimIndent()
        val rule = RuleAstToBuilderMapper.map(rule = Parser(input = dsl).parseRules().single())
        assertTrue(actual = rule is BuilderRule.Supported)

        assertEquals(
            expected = listOf("budget"),
            actual = RibbonModel.readsOf(rule = rule),
            message = "a variable at any depth reaches the rule, so the ribbon must show it",
        )
    }
}
