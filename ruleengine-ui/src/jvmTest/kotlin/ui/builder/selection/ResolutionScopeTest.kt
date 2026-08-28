package ui.builder.selection

import ruleengine.dsl.parser.Parser
import ruleengine.schema.FieldSchemaLoader
import ui.builder.OperandRules
import ui.builder.RuleAstToBuilderMapper
import ui.builder.model.BuilderOperand
import ui.builder.model.BuilderRule
import ui.builder.model.catalog.BuilderCatalog
import ui.builder.model.catalog.fieldAtPath
import ui.builder.model.mutable.BuilderEditorState
import ui.builder.model.mutable.MutableConditionNode
import ui.builder.model.names
import ui.builder.model.selection.SelectionStep
import ui.workbench.builderCatalogFieldsFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a selected element resolves — the two facts the inspector used to lose on the way down.
 *
 * Both losses surfaced as the same complaint: *"'x' is not declared in the schema"* about a field that
 * plainly is. A path segment opened on its own was resolved against the schema's top-level fields
 * instead of against its own parent, and a filter's operands were resolved against the document
 * instead of against the collection element they restrict.
 *
 * The rules here are the ones from `.plan/v1`, unedited, because that is the project the reports came
 * from.
 */
class ResolutionScopeTest {

    private val schema = FieldSchemaLoader.loadFromString(
        content = """
            schema: scope-v1

            fields:
              reports:
                type: object
                fields:
                  income:
                    type: object
                    fields:
                      daysOfReport: {type: integer, alias: TRANSACTION_HISTORY_DAYS}
                      completeMonths: {type: string_set}
                      monthlyData:
                        type: object
                        fields:
                          totalIncome:
                            type: object
                            fields:
                              totalMonthlyAmounts:
                                type: collection
                                fields:
                                  month: {type: text}
                                  totalAmount: {type: decimal}
                                  transactionsCount: {type: integer}
        """.trimIndent()
    )

    private val catalog: BuilderCatalog = builderCatalogFieldsFrom(schema = schema)

    /** The collection path every filter case below hangs off, as the rules spell it. */
    private val collection =
        listOf("reports", "income", "monthlyData", "totalIncome", "totalMonthlyAmounts")

    private val collectionPath = collection.joinToString(separator = ".")

    /** `02-activity.rule`, reduced to the one restriction that matters here. */
    private val filtered = "count($collectionPath[month in reports.income.completeMonths]) > 0"

    private fun stateOf(condition: String): BuilderEditorState {
        val text = """
            rule "under-test" {
              when
                $condition
              then
                outcome "GREEN"
            }
        """.trimIndent()
        val ast = Parser(input = text).parseRules().single()
        val rule = assertIs<BuilderRule.Supported>(value = RuleAstToBuilderMapper.map(rule = ast))
        return BuilderEditorState.fromBuilderRule(rule = rule)
    }

    private fun BuilderEditorState.rowId(): String = conditionNodes.first().id

    private fun target(state: BuilderEditorState, steps: List<SelectionStep>) =
        SelectionResolver.resolveCondition(
            state = state,
            conditionId = state.rowId(),
            steps = steps,
            catalog = catalog,
        )

    // ── a segment opened on its own ───────────────────────────────────────────

    @Test
    fun `a segment carries the segments in front of it, so it resolves at its own depth`() {
        val state = stateOf(condition = "count($collectionPath) > 0")
        val third = target(
            state = state,
            steps = listOf(SelectionStep.Left, SelectionStep.Segment(index = 2)),
        )
        val segment = assertIs<SelectionTarget.Segment>(value = third)

        assertEquals(expected = "monthlyData", actual = segment.segment.name)
        assertEquals(
            expected = listOf("reports", "income"),
            actual = segment.scope.prefix.names,
            message = "Without the prefix this resolves against the schema's top-level fields",
        )

        // What the step card computes: the segment is declared, and its dropdown offers the members
        // of the level it sits at rather than the schema's top-level fields.
        val whole = segment.scope.prefix + segment.segment
        assertNotNull(actual = catalog.fieldAtPath(segments = whole.names))
        assertEquals(
            expected = listOf("daysOfReport", "completeMonths", "monthlyData"),
            actual = OperandRules
                .segmentOptions(fields = catalog, path = whole, depth = whole.lastIndex)
                .map { it.id },
        )
        assertEquals(
            expected = listOf("totalIncome"),
            actual = OperandRules
                .segmentOptions(fields = catalog, path = whole, depth = whole.size)
                .map { it.id },
            message = "and stepping into it offers what it contains",
        )
    }

    @Test
    fun `resolving that same segment against an empty prefix is what reported it undeclared`() {
        // The old behaviour, kept as the contrast: one step, no prefix, resolved from the root.
        assertNull(actual = catalog.fieldAtPath(segments = listOf("monthlyData")))
    }

    // ── operands inside a where ───────────────────────────────────────────────

    @Test
    fun `a filter's left side resolves against the element, not the document`() {
        val state = stateOf(condition = filtered)
        val left = target(
            state = state,
            steps = listOf(
                SelectionStep.Left,
                SelectionStep.Segment(index = collection.lastIndex),
                SelectionStep.Filter(index = 0),
                SelectionStep.Left,
            ),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = left)

        assertEquals(
            expected = "integer",
            actual = operand.scope.catalog.fieldAtPath(segments = listOf("transactionsCount"))?.type,
            message = "The element's members are what a restriction names",
        )
        assertNotNull(
            actual = operand.scope.catalog.fieldAtPath(segments = listOf("month")),
            message = "`month` is a member of the element; against the document it is nothing",
        )
    }

    @Test
    fun `a filter's right side still reaches the document behind the element`() {
        val state = stateOf(condition = filtered)
        val right = target(
            state = state,
            steps = listOf(
                SelectionStep.Left,
                SelectionStep.Segment(index = collection.lastIndex),
                SelectionStep.Filter(index = 0),
                SelectionStep.Right,
            ),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = right)

        assertEquals(
            expected = "string_set",
            actual = operand.scope.catalog
                .fieldAtPath(segments = listOf("reports", "income", "completeMonths"))?.type,
            message = "The engine overlays the element on the document; both halves must resolve",
        )
    }

    @Test
    fun `a segment and its filter resolve as one selection`() {
        // The step card drills to both at once. Emitting them as two separate selections meant the
        // second replaced the first and the panel reported the selection as edited away.
        val state = stateOf(condition = filtered)
        val filter = target(
            state = state,
            steps = listOf(
                SelectionStep.Left,
                SelectionStep.Segment(index = collection.lastIndex),
                SelectionStep.Filter(index = 0),
            ),
        )

        assertIs<SelectionTarget.Filter>(value = filter)
        assertNull(
            actual = target(state = state, steps = listOf(SelectionStep.Left, SelectionStep.Filter(index = 0))),
            message = "A filter step without its segment resolves to nothing — that was the symptom",
        )
    }

    // ── a bare alias as a whole path ──────────────────────────────────────────

    @Test
    fun `a bare alias is the field a simple row names, and the catalog resolves it`() {
        // `.plan/v1`, 01-eligibility.rule. A symbolic operator against a literal stays a simple row.
        val state = stateOf(condition = "TRANSACTION_HISTORY_DAYS >= 85")
        val row = assertIs<MutableConditionNode.Leaf>(value = state.conditionNodes.first())

        assertEquals(expected = "TRANSACTION_HISTORY_DAYS", actual = row.inner.field)
        assertTrue(
            actual = catalog.fieldAtPath(segments = listOf(row.inner.field)) != null,
            message = "The dropdown has always offered this spelling; the path walk used to reject it",
        )
    }

    @Test
    fun `a bare alias inside an operand path is declared, which is the step card's own check`() {
        val state = stateOf(condition = "abs(TRANSACTION_HISTORY_DAYS) >= 85")
        val argument = target(
            state = state,
            steps = listOf(SelectionStep.Left, SelectionStep.Argument(index = 0)),
        )
        val operand = assertIs<SelectionTarget.Operand>(value = argument)
        val path = assertIs<BuilderOperand.FieldRef>(value = operand.operand).path

        assertEquals(expected = listOf("TRANSACTION_HISTORY_DAYS"), actual = path.names)
        assertNotNull(
            actual = operand.scope.catalog.fieldAtPath(segments = path.names),
            message = "This is exactly what StepCard passes as `declared`",
        )
    }
}
