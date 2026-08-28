package ui.builder.selection

import ruleengine.core.domain.dto.RuleBranch
import ui.builder.OperandText
import ui.builder.model.BuilderAction
import ui.builder.model.BuilderConditionNode
import ui.builder.model.BuilderExtraction
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderPathDecoration
import ui.builder.model.BuilderPathStep
import ui.builder.model.BuilderRule
import ui.builder.model.BuilderTerm
import ui.builder.model.BuilderVariable
import ui.builder.model.filter
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.pathOperand
import ui.builder.model.selection.SelectionStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The selection walk, and the setters it hands back.
 *
 * The setters are the part worth testing. A nested operand is an immutable value inside other
 * immutable values, held in one Compose state slot on the row, so "resolve" is only half the job —
 * writing an edit six levels down has to rebuild every value above it. Each test here edits through a
 * resolved target and then asserts the *whole row's* generated DSL, because that is the only evidence
 * the rebuild reached the slot.
 */
class SelectionResolverTest {

    // ── fixtures ──────────────────────────────────────────────────────────────

    /** `abs(sum(invoices.amount) - sum(payments.amount)) > 1000` — the four-level case. */
    private fun deepComparisonState(): BuilderEditorState {
        val aggregate = { collection: String ->
            BuilderOperand.Aggregate(
                function = "sum",
                path = listOf(BuilderPathStep(name = collection), BuilderPathStep(name = "amount")),
            )
        }
        val call = BuilderOperand.Call(
            function = "abs",
            args = listOf(
                BuilderOperand.Calc(
                    terms = listOf(
                        BuilderTerm(operator = "", operand = aggregate("invoices")),
                        BuilderTerm(operator = "-", operand = aggregate("payments")),
                    ),
                ),
            ),
        )
        return BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "balance-drift",
                conditionNodes = listOf(
                    BuilderConditionNode.Comparison(
                        nodeId = "cmp-1",
                        left = call,
                        operator = ">",
                        right = BuilderOperand.Literal(text = "1000", numeric = true),
                    ),
                ),
                actions = emptyList(),
            ),
        )
    }

    /** `sum(invoices[customerId in priorityCustomerIds].amount) > 10000` — a filtered path. */
    private fun filteredPathState(): BuilderEditorState {
        val segment = BuilderPathStep(
            name = "invoices",
            decorations = listOf(
                BuilderPathDecoration.Filter(
                    filter = filter(field = "customerId", operator = "in", value = "priorityCustomerIds"),
                ),
            ),
        )
        return BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "priority-exposure",
                conditionNodes = listOf(
                    BuilderConditionNode.Comparison(
                        nodeId = "cmp-1",
                        left = BuilderOperand.Aggregate(
                            function = "sum",
                            path = listOf(segment, BuilderPathStep(name = "amount")),
                        ),
                        operator = ">",
                        right = BuilderOperand.Literal(text = "10000", numeric = true),
                    ),
                ),
                actions = emptyList(),
            ),
        )
    }

    private fun leftDsl(state: BuilderEditorState): String {
        val comparison = assertIs<ui.builder.model.mutable.MutableConditionNode.ComparisonLeaf>(
            value = state.conditionNodes.first(),
        )
        return OperandText.toDsl(operand = comparison.inner.left)
    }

    // ── anchors ───────────────────────────────────────────────────────────────

    @Test
    fun `an empty step list resolves the row itself`() {
        val state = deepComparisonState()
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = emptyList(),
        )
        assertIs<SelectionTarget.Comparison>(value = target)
    }

    @Test
    fun `an unknown id resolves to nothing`() {
        val state = deepComparisonState()
        assertNull(
            actual = SelectionResolver.resolveCondition(
                state = state,
                conditionId = "no-such-row",
                steps = emptyList(),
            ),
        )
    }

    @Test
    fun `a simple condition has no operands to walk into`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "rent",
                conditionNodes = listOf(
                    BuilderConditionNode.Condition(
                        nodeId = "cond-1",
                        field = "amount",
                        operator = ">=",
                        value = "300",
                    ),
                ),
                actions = emptyList(),
            ),
        )
        assertNull(
            actual = SelectionResolver.resolveCondition(
                state = state,
                conditionId = "cond-1",
                steps = listOf(SelectionStep.Left),
            ),
        )
    }

    // ── the walk, four levels down ────────────────────────────────────────────

    @Test
    fun `resolves an aggregate four levels inside the left operand`() {
        val state = deepComparisonState()
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            // left → abs's argument → the calculation's second term → that term's operand
            steps = listOf(SelectionStep.Left, SelectionStep.Argument(index = 0), SelectionStep.Term(index = 1)),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = target)
        assertEquals(expected = "sum(payments.amount)", actual = OperandText.toDsl(operand = operand.operand))
    }

    @Test
    fun `writing through a four-level setter rebuilds the whole row`() {
        val state = deepComparisonState()
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = listOf(SelectionStep.Left, SelectionStep.Argument(index = 0), SelectionStep.Term(index = 1)),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = target)

        operand.write(
            BuilderOperand.Aggregate(
                function = "avg",
                path = listOf(BuilderPathStep(name = "payments"), BuilderPathStep(name = "amount")),
            ),
        )

        assertEquals(
            expected = "abs(sum(invoices.amount) - avg(payments.amount))",
            actual = leftDsl(state = state),
        )
    }

    @Test
    fun `resolves and edits a path segment inside an aggregate`() {
        val state = deepComparisonState()
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = listOf(
                SelectionStep.Left,
                SelectionStep.Argument(index = 0),
                SelectionStep.Term(index = 0),
                SelectionStep.Segment(index = 0),
            ),
        )
        val segment = assertIs<SelectionTarget.Segment>(value = target)
        assertEquals(expected = "invoices", actual = segment.segment.name)

        segment.write(segment.segment.copy(name = "creditNotes"))

        assertEquals(
            expected = "abs(sum(creditNotes.amount) - sum(payments.amount))",
            actual = leftDsl(state = state),
        )
    }

    // ── filters, which are comparisons one level down ─────────────────────────

    @Test
    fun `resolves a where filter on a path segment`() {
        val state = filteredPathState()
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = listOf(SelectionStep.Left, SelectionStep.Segment(index = 0), SelectionStep.Filter(index = 0)),
        )
        val filter = assertIs<SelectionTarget.Filter>(value = target)
        assertEquals(expected = "in", actual = filter.filter.operator)
    }

    @Test
    fun `writing a filter's right side rebuilds path, aggregate and row`() {
        val state = filteredPathState()
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cmp-1",
            steps = listOf(
                SelectionStep.Left,
                SelectionStep.Segment(index = 0),
                SelectionStep.Filter(index = 0),
                SelectionStep.Right,
            ),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = target)

        operand.write(pathOperand(dotted = "watchlistIds"))

        assertEquals(
            expected = "sum(invoices[customerId in watchlistIds].amount)",
            actual = leftDsl(state = state),
        )
    }

    // ── statements ────────────────────────────────────────────────────────────

    @Test
    fun `resolves and edits an assignment's value`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "totals",
                conditionNodes = emptyList(),
                actions = emptyList(),
                variables = listOf(
                    BuilderVariable(
                        id = "var-1",
                        name = "totalWeightKg",
                        expression = BuilderOperand.Aggregate(
                            function = "sum",
                            path = listOf(BuilderPathStep(name = "parcels"), BuilderPathStep(name = "weightKg")),
                        ),
                    ),
                ),
            ),
        )
        val target = SelectionResolver.resolveStatement(
            state = state,
            branch = RuleBranch.THEN,
            statementId = "var-1",
            steps = listOf(SelectionStep.Value),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = target)
        assertEquals(expected = "sum(parcels.weightKg)", actual = OperandText.toDsl(operand = operand.operand))

        operand.write(
            BuilderOperand.Aggregate(
                function = "max",
                path = listOf(BuilderPathStep(name = "parcels"), BuilderPathStep(name = "weightKg")),
            ),
        )
        assertEquals(
            expected = "max(parcels.weightKg)",
            actual = OperandText.toDsl(operand = state.variables.first().expression),
        )
    }

    @Test
    fun `resolves and edits an action's extract clause`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "tag",
                conditionNodes = emptyList(),
                actions = listOf(
                    BuilderAction(
                        id = "act-1",
                        name = "label",
                        arguments = listOf("$1"),
                        extraction = BuilderExtraction(
                            sourceField = "purpose",
                            pattern = "RENT-([0-9]+)",
                            groupIndex = 1,
                        ),
                    ),
                ),
            ),
        )
        val target = SelectionResolver.resolveStatement(
            state = state,
            branch = RuleBranch.THEN,
            statementId = "act-1",
            steps = listOf(SelectionStep.Extraction),
        )
        val extraction = assertIs<SelectionTarget.Extraction>(value = target)
        assertEquals(expected = "RENT-([0-9]+)", actual = extraction.extraction.pattern)

        extraction.write(extraction.extraction.copy(pattern = "INV-([0-9]+)"))
        assertEquals(expected = "INV-([0-9]+)", actual = state.actions.first().extraction?.pattern)
    }

    // ── stale selections ──────────────────────────────────────────────────────

    @Test
    fun `a step past the end of a list resolves to nothing rather than throwing`() {
        val state = deepComparisonState()
        assertNull(
            actual = SelectionResolver.resolveCondition(
                state = state,
                conditionId = "cmp-1",
                steps = listOf(SelectionStep.Left, SelectionStep.Argument(index = 7)),
            ),
        )
    }

    @Test
    fun `a step that does not fit the operand resolves to nothing`() {
        val state = deepComparisonState()
        assertNull(
            actual = SelectionResolver.resolveCondition(
                state = state,
                conditionId = "cmp-1",
                // the right side is a literal, which has no arguments
                steps = listOf(SelectionStep.Right, SelectionStep.Argument(index = 0)),
            ),
        )
    }

    @Test
    fun `finds a row nested inside a group`() {
        val state = BuilderEditorState.fromBuilderRule(
            rule = BuilderRule.Supported(
                id = "rent-payment",
                conditionNodes = listOf(
                    BuilderConditionNode.Group(
                        nodeId = "grp-1",
                        nodes = listOf(
                            BuilderConditionNode.Condition(
                                nodeId = "cond-9",
                                field = "purpose",
                                operator = "contains",
                                value = "rent",
                            ),
                        ),
                    ),
                ),
                actions = emptyList(),
            ),
        )
        val target = SelectionResolver.resolveCondition(
            state = state,
            conditionId = "cond-9",
            steps = emptyList(),
        )
        val condition = assertIs<SelectionTarget.Condition>(value = target)
        assertEquals(expected = "purpose", actual = condition.condition.field)
    }
}
